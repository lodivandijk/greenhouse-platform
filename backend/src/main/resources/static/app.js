(function () {
    "use strict";

    const CONFIG = {
        stateEndpoint: "/api/v1/state",
        refreshIntervalMs: 20_000
    };

    const dashboardState = {
        lastSuccessfulResponse: null,
        lastSuccessfulRefreshAt: null,
        refreshTimerId: null,
        requestInFlight: false
    };

    const TWIN_STATUS_LABELS = {
        NORMAL: "Online",
        OFFLINE: "Offline",
        UNKNOWN: "Unknown"
    };

    const TWIN_STATUS_INDICATOR = {
        NORMAL: "online",
        OFFLINE: "offline",
        UNKNOWN: "unknown"
    };

    const DEVICE_STATUS_LABELS = {
        ONLINE: "Online",
        DELAYED: "Delayed",
        OFFLINE: "Offline",
        UNKNOWN: "Unknown"
    };

    const FRESHNESS_LABELS = {
        CURRENT: "Current",
        DELAYED: "Delayed",
        STALE: "Stale",
        UNKNOWN: "Unknown"
    };

    const SEVERITY_LABELS = {
        CRITICAL: "Critical",
        WARNING: "Warning",
        ADVISORY: "Advisory"
    };

    const SEVERITY_RANK = {
        CRITICAL: 3,
        WARNING: 2,
        ADVISORY: 1
    };

    const ASSESSMENT_CODE_LABELS = {
        TEMPERATURE_BELOW_LIMIT: "Low temperature",
        TEMPERATURE_ABOVE_LIMIT: "High temperature",
        HUMIDITY_BELOW_LIMIT: "Low humidity",
        HUMIDITY_ABOVE_LIMIT: "High humidity",
        OBSERVATION_STALE: "Stale sensor data",
        DEVICE_OFFLINE: "Device offline"
    };

    let dom = {};

    document.addEventListener("DOMContentLoaded", init);

    function init() {
        dom = {
            fullPageError: document.getElementById("full-page-error"),
            appShell: document.getElementById("app-shell"),
            dashboardMain: document.getElementById("dashboard-main"),
            greenhouseName: document.getElementById("greenhouse-name"),
            connectivityDot: document.getElementById("connectivity-dot"),
            connectivityLabel: document.getElementById("connectivity-label"),
            lastRefreshedTime: document.getElementById("last-refreshed-time"),
            refreshButton: document.getElementById("refresh-button"),
            apiWarning: document.getElementById("api-warning"),
            apiWarningMessage: document.getElementById("api-warning-message"),
            environmentCards: document.getElementById("environment-cards"),
            assessmentsList: document.getElementById("assessments-list"),
            systemStatus: document.getElementById("system-status"),
            footerApiState: document.getElementById("footer-api-state"),
            footerRefreshInterval: document.getElementById("footer-refresh-interval")
        };

        dom.footerRefreshInterval.textContent = String(CONFIG.refreshIntervalMs / 1000);
        dom.refreshButton.addEventListener("click", handleManualRefresh);
        document.addEventListener("visibilitychange", handleVisibilityChange);

        refresh();
        startPolling();
    }

    async function fetchState() {
        const response = await fetch(CONFIG.stateEndpoint, {
            headers: { Accept: "application/json" }
        });

        if (!response.ok) {
            throw new Error("Greenhouse state request failed with status " + response.status);
        }

        return response.json();
    }

    async function refresh() {
        if (dashboardState.requestInFlight) {
            return;
        }

        dashboardState.requestInFlight = true;
        setRefreshButtonBusy(true);

        try {
            const apiResponse = await fetchState();
            dashboardState.lastSuccessfulResponse = apiResponse;
            dashboardState.lastSuccessfulRefreshAt = new Date();

            hideApiWarning();
            hideFullPageError();
            renderDashboard(mapApiState(apiResponse), { stale: false });
        } catch (error) {
            console.error("Failed to retrieve greenhouse state");
            handleFetchFailure();
        } finally {
            dashboardState.requestInFlight = false;
            setRefreshButtonBusy(false);
        }
    }

    function handleFetchFailure() {
        if (dashboardState.lastSuccessfulResponse === null) {
            renderFullPageError();
            return;
        }

        renderApiWarning();
        renderDashboard(mapApiState(dashboardState.lastSuccessfulResponse), { stale: true });
    }

    function handleManualRefresh() {
        refresh();
    }

    function startPolling() {
        stopPolling();
        dashboardState.refreshTimerId = window.setInterval(refresh, CONFIG.refreshIntervalMs);
    }

    function stopPolling() {
        if (dashboardState.refreshTimerId !== null) {
            window.clearInterval(dashboardState.refreshTimerId);
            dashboardState.refreshTimerId = null;
        }
    }

    function handleVisibilityChange() {
        if (document.hidden) {
            stopPolling();
            return;
        }

        refresh();
        startPolling();
    }

    // Adapts the raw API response into a stable presentation model.
    // Field names only - no thresholds, no environmental interpretation.
    function mapApiState(apiResponse) {
        const twin = (apiResponse && apiResponse.twin) || {};
        const zones = Array.isArray(twin.zones) ? twin.zones : [];
        const primaryZone = zones.length > 0 ? zones[0] : null;
        const devices = primaryZone && Array.isArray(primaryZone.devices) ? primaryZone.devices : [];
        const primaryDevice = devices.length > 0 ? devices[0] : null;
        const environment = primaryZone ? primaryZone.environment : null;
        const dataQuality = primaryZone ? primaryZone.dataQuality : null;
        const rawAssessments = Array.isArray(apiResponse.assessments) ? apiResponse.assessments : [];

        return {
            greenhouse: {
                id: twin.greenhouseId || null,
                name: twin.name || twin.greenhouseId || null,
                status: twin.status || null
            },
            zone: primaryZone ? { id: primaryZone.zoneId, name: primaryZone.name } : null,
            environment: {
                temperatureCelsius: environment ? environment.temperatureCelsius : null,
                humidityPercent: environment ? environment.humidityPercent : null,
                pressureHpa: environment ? environment.pressureHpa : null
            },
            dataQuality: dataQuality ? {
                freshness: dataQuality.freshness || null,
                ageSeconds: typeof dataQuality.ageSeconds === "number" ? dataQuality.ageSeconds : null,
                observedAt: dataQuality.observedAt || null
            } : null,
            device: primaryDevice ? {
                id: primaryDevice.deviceId || null,
                status: primaryDevice.status || null,
                lastSeenAt: primaryDevice.lastSeenAt || null
            } : null,
            assessments: rawAssessments.map(mapAssessment)
        };
    }

    function mapAssessment(raw) {
        return {
            id: raw.id,
            code: raw.code || null,
            severity: raw.severity || null,
            message: raw.message || null,
            zoneId: raw.zoneId || null,
            deviceId: raw.deviceId || null,
            firstDetectedAt: raw.firstDetectedAt || null,
            evidence: raw.evidence && typeof raw.evidence === "object" ? raw.evidence : null
        };
    }

    function renderDashboard(model, options) {
        const stale = Boolean(options && options.stale);

        dom.dashboardMain.classList.toggle("dashboard--stale", stale);
        dom.footerApiState.textContent = stale ? "Reconnecting…" : "Connected";

        renderHeader(model, stale);
        renderEnvironment(model);
        renderAssessments(model);
        renderSystemStatus(model, stale);
    }

    function renderHeader(model, stale) {
        dom.greenhouseName.textContent = model.greenhouse.name || "Greenhouse";

        const indicator = TWIN_STATUS_INDICATOR[model.greenhouse.status] || "unknown";
        dom.connectivityDot.dataset.status = indicator;
        dom.connectivityLabel.textContent = TWIN_STATUS_LABELS[model.greenhouse.status] || "Unknown";

        const refreshedAt = dashboardState.lastSuccessfulRefreshAt;
        if (refreshedAt) {
            dom.lastRefreshedTime.textContent = formatRelativeTime(refreshedAt);
            dom.lastRefreshedTime.setAttribute("datetime", refreshedAt.toISOString());
            dom.lastRefreshedTime.setAttribute("title", formatAbsoluteDateTime(refreshedAt));
        }
    }

    function renderEnvironment(model) {
        const cards = [
            buildMetricCard("Temperature", model.environment.temperatureCelsius, "°C", 1),
            buildMetricCard("Humidity", model.environment.humidityPercent, "%", 0),
            buildMetricCard("Pressure", model.environment.pressureHpa, "hPa", 0)
        ];

        dom.environmentCards.replaceChildren(...cards);
    }

    function buildMetricCard(label, value, unit, precision) {
        const card = document.createElement("div");
        card.className = "metric-card";

        const labelEl = document.createElement("p");
        labelEl.className = "metric-card__label";
        labelEl.textContent = label;
        card.appendChild(labelEl);

        const valueEl = document.createElement("p");
        if (hasValue(value)) {
            valueEl.className = "metric-card__value";
            valueEl.textContent = value.toFixed(precision);
            const unitEl = document.createElement("span");
            unitEl.className = "metric-card__unit";
            unitEl.textContent = unit;
            valueEl.appendChild(unitEl);
        } else {
            valueEl.className = "metric-card__value metric-card__value--unavailable";
            valueEl.textContent = "Not available";
        }
        card.appendChild(valueEl);

        return card;
    }

    function renderAssessments(model) {
        const assessments = sortAssessments(model.assessments);

        if (assessments.length === 0) {
            dom.assessmentsList.replaceChildren(buildEmptyAssessmentsState(model.greenhouse.status));
            return;
        }

        const cards = assessments.map((assessment) => buildAssessmentCard(assessment, model.zone));
        dom.assessmentsList.replaceChildren(...cards);
    }

    function sortAssessments(assessments) {
        return [...assessments].sort((a, b) => {
            const rankDiff = (SEVERITY_RANK[b.severity] || 0) - (SEVERITY_RANK[a.severity] || 0);
            if (rankDiff !== 0) {
                return rankDiff;
            }
            const aTime = a.firstDetectedAt ? Date.parse(a.firstDetectedAt) : 0;
            const bTime = b.firstDetectedAt ? Date.parse(b.firstDetectedAt) : 0;
            return aTime - bTime;
        });
    }

    function buildEmptyAssessmentsState(twinStatus) {
        const container = document.createElement("div");
        const title = document.createElement("p");
        const message = document.createElement("p");
        message.className = "assessments-empty__message";

        if (twinStatus === "NORMAL") {
            container.className = "assessments-empty";
            title.textContent = "No active assessments";
            message.textContent = "Environmental conditions are currently within the configured operating thresholds.";
        } else {
            container.className = "assessments-empty assessments-empty--unavailable";
            title.textContent = "No active assessments are available.";
            message.textContent = "Current environmental conditions cannot be confirmed because recent sensor data is unavailable.";
        }

        title.className = "assessments-empty__title";
        container.appendChild(title);
        container.appendChild(message);
        return container;
    }

    function buildAssessmentCard(assessment, primaryZone) {
        const severityClass = (assessment.severity || "").toLowerCase();
        const card = document.createElement("article");
        card.className = "assessment-card assessment-card--" + (severityClass || "advisory");

        const header = document.createElement("div");
        header.className = "assessment-card__header";

        const severityBadge = document.createElement("span");
        severityBadge.className = "assessment-card__severity";
        severityBadge.textContent = SEVERITY_LABELS[assessment.severity] || "Unknown";
        header.appendChild(severityBadge);

        const title = document.createElement("h3");
        title.className = "assessment-card__title";
        title.textContent = mapAssessmentCode(assessment.code);
        header.appendChild(title);

        card.appendChild(header);

        if (assessment.message) {
            const message = document.createElement("p");
            message.className = "assessment-card__message";
            message.textContent = assessment.message;
            card.appendChild(message);
        }

        const details = buildAssessmentDetails(assessment, primaryZone);
        if (details) {
            card.appendChild(details);
        }

        return card;
    }

    function buildAssessmentDetails(assessment, primaryZone) {
        const rows = [];

        if (assessment.zoneId) {
            const zoneLabel = primaryZone && primaryZone.id === assessment.zoneId
                ? (primaryZone.name || primaryZone.id)
                : assessment.zoneId;
            rows.push(["Zone", zoneLabel]);
        }

        if (assessment.deviceId) {
            rows.push(["Device", assessment.deviceId]);
        }

        if (assessment.firstDetectedAt) {
            rows.push(["Active since", formatAbsoluteTime(assessment.firstDetectedAt)]);
        }

        if (assessment.evidence) {
            Object.keys(assessment.evidence).forEach((key) => {
                const formatted = formatEvidenceValue(key, assessment.evidence[key]);
                if (formatted !== null) {
                    rows.push([humanizeKey(key), formatted]);
                }
            });
        }

        if (rows.length === 0) {
            return null;
        }

        const dl = document.createElement("dl");
        dl.className = "assessment-card__details";

        rows.forEach(([label, value]) => {
            const row = document.createElement("div");
            const dt = document.createElement("dt");
            dt.textContent = label;
            const dd = document.createElement("dd");
            dd.textContent = value;
            row.appendChild(dt);
            row.appendChild(dd);
            dl.appendChild(row);
        });

        return dl;
    }

    function renderSystemStatus(model, stale) {
        const rows = [
            ["Greenhouse ID", model.greenhouse.id || "Not available"],
            ["Zone", model.zone ? (model.zone.name || model.zone.id) : "Not available"],
            ["Device", model.device ? model.device.id : "Not available"],
            ["Device status", model.device ? (DEVICE_STATUS_LABELS[model.device.status] || "Unknown") : "Not available"],
            ["Data freshness", model.dataQuality ? (FRESHNESS_LABELS[model.dataQuality.freshness] || "Unknown") : "Not available"],
            ["Latest observation", model.dataQuality && model.dataQuality.observedAt
                ? formatAbsoluteTime(model.dataQuality.observedAt)
                : "Not available"],
            ["Data age", model.dataQuality && hasValue(model.dataQuality.ageSeconds)
                ? formatDuration(model.dataQuality.ageSeconds)
                : "Not available"],
            ["Active assessments", String(model.assessments.length)],
            ["API status", stale ? "Reconnecting…" : "Connected"]
        ];

        const rowElements = rows.map(([label, value]) => {
            const row = document.createElement("div");
            const dt = document.createElement("dt");
            dt.textContent = label;
            const dd = document.createElement("dd");
            dd.textContent = value;
            row.appendChild(dt);
            row.appendChild(dd);
            return row;
        });

        dom.systemStatus.replaceChildren(...rowElements);
    }

    function renderFullPageError() {
        dom.fullPageError.hidden = false;
        dom.appShell.hidden = true;
    }

    function hideFullPageError() {
        dom.fullPageError.hidden = true;
        dom.appShell.hidden = false;
    }

    function renderApiWarning() {
        const relative = dashboardState.lastSuccessfulRefreshAt
            ? formatRelativeTime(dashboardState.lastSuccessfulRefreshAt)
            : "an earlier update";
        dom.apiWarningMessage.textContent =
            "The Greenhouse API could not be reached. The values below are from the last successful update " + relative + ".";
        dom.apiWarning.hidden = false;
    }

    function hideApiWarning() {
        dom.apiWarning.hidden = true;
    }

    function setRefreshButtonBusy(busy) {
        dom.refreshButton.disabled = busy;
        dom.refreshButton.setAttribute("aria-busy", String(busy));
        dom.refreshButton.textContent = busy ? "Refreshing…" : "Refresh";
    }

    function mapAssessmentCode(code) {
        if (ASSESSMENT_CODE_LABELS[code]) {
            return ASSESSMENT_CODE_LABELS[code];
        }
        if (!code) {
            return "Assessment";
        }
        return code
            .toLowerCase()
            .split("_")
            .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
            .join(" ");
    }

    function humanizeKey(key) {
        const spaced = key.replace(/([a-z0-9])([A-Z])/g, "$1 $2");
        return spaced.charAt(0).toUpperCase() + spaced.slice(1);
    }

    function formatEvidenceValue(key, value) {
        if (value === null || value === undefined) {
            return null;
        }
        if (typeof value === "string" && /At$/.test(key)) {
            const formatted = formatAbsoluteTime(value);
            return formatted || value;
        }
        return String(value);
    }

    function hasValue(value) {
        return value !== null && value !== undefined && !(typeof value === "number" && Number.isNaN(value));
    }

    function toDate(value) {
        if (value instanceof Date) {
            return Number.isNaN(value.getTime()) ? null : value;
        }
        if (!value) {
            return null;
        }
        const parsed = new Date(value);
        return Number.isNaN(parsed.getTime()) ? null : parsed;
    }

    function formatRelativeTime(value) {
        const date = toDate(value);
        if (!date) {
            return "unknown";
        }

        const diffSeconds = Math.max(0, Math.round((Date.now() - date.getTime()) / 1000));

        if (diffSeconds < 5) {
            return "just now";
        }
        if (diffSeconds < 60) {
            return diffSeconds + " seconds ago";
        }
        const diffMinutes = Math.round(diffSeconds / 60);
        if (diffMinutes < 60) {
            return diffMinutes + (diffMinutes === 1 ? " minute ago" : " minutes ago");
        }
        const diffHours = Math.round(diffMinutes / 60);
        if (diffHours < 24) {
            return diffHours + (diffHours === 1 ? " hour ago" : " hours ago");
        }
        const diffDays = Math.round(diffHours / 24);
        return diffDays + (diffDays === 1 ? " day ago" : " days ago");
    }

    function formatAbsoluteTime(value) {
        const date = toDate(value);
        if (!date) {
            return "Not available";
        }
        return date.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    }

    function formatAbsoluteDateTime(value) {
        const date = toDate(value);
        if (!date) {
            return "";
        }
        return date.toLocaleString("en-GB");
    }

    function formatDuration(seconds) {
        if (seconds < 60) {
            return seconds + (seconds === 1 ? " second" : " seconds");
        }
        const minutes = Math.round(seconds / 60);
        if (minutes < 60) {
            return minutes + (minutes === 1 ? " minute" : " minutes");
        }
        const hours = Math.round(minutes / 60);
        return hours + (hours === 1 ? " hour" : " hours");
    }
})();
