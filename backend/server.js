const express = require('express');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 5000;

// Enable CORS for Vercel Frontend & local dev
app.use(cors({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(express.json());

// In-Memory Gaming Sessions Store
let sessions = [
    {
        id: 1,
        gameLabel: "COSMIC_VOID_v1.0",
        startTimeMs: Date.now() - 1920000,
        endTimeMs: Date.now(),
        durationSeconds: 1920,
        sessionTimeFormatted: "32 min",
        efficiencyPct: 94.2,
        throttlingEventsAvoided: 3,
        avgFps: 58.4,
        minFps: 48.0,
        onePercentLowFps: 51.0,
        peakTemp: 43.2,
        avgTemp: 37.8,
        grade: "S",
        gradeScore: 94,
        summaryFeedback: "Outstanding thermal stability! Predictive adjustments prevented 3 major throttle events."
    },
    {
        id: 2,
        gameLabel: "CYBER_OVERDRIVE",
        startTimeMs: Date.now() - 3600000,
        endTimeMs: Date.now() - 1680000,
        durationSeconds: 2700,
        sessionTimeFormatted: "45 min",
        efficiencyPct: 88.6,
        throttlingEventsAvoided: 5,
        avgFps: 57.1,
        minFps: 42.0,
        onePercentLowFps: 46.0,
        peakTemp: 44.1,
        avgTemp: 39.2,
        grade: "A",
        gradeScore: 86,
        summaryFeedback: "Great thermal pacing! Minor temperature climb detected during intense combat scenes."
    }
];

// ==========================================
// 1. HEALTH CHECK ENDPOINT (Render Monitor)
// ==========================================
app.get('/health', (req, res) => {
    res.json({
        status: "ok",
        service: "Thermal & Performance Guardian Backend API",
        version: "1.0.0",
        timestamp: new Date().toISOString(),
        activeSessions: sessions.length
    });
});

// Root route
app.get('/', (req, res) => {
    res.json({
        service: "Thermal & Performance Guardian Backend API",
        status: "online",
        endpoints: {
            health: "/health",
            predict: "POST /api/telemetry/predict",
            sessions: "GET & POST /api/sessions",
            sessionById: "GET /api/sessions/:id",
            stats: "GET /api/stats"
        }
    });
});

// ==========================================
// 2. PREDICTIVE TELEMETRY ENGINE ENDPOINT
// ==========================================
app.post('/api/telemetry/predict', (req, res) => {
    const {
        currentTemp = 36.0,
        currentFps = 60.0,
        batteryPercent = 82,
        batteryDrainRate = 0.3,
        telemetryHistory = []
    } = req.body;

    // 1. Calculate OLS Linear Regression Slope (°C / 10s)
    let slopePer10s = 0.0;
    if (Array.isArray(telemetryHistory) && telemetryHistory.length >= 3) {
        const recent = telemetryHistory.slice(-15);
        const n = recent.length;
        let sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        
        for (let i = 0; i < n; i++) {
            const x = i;
            const y = recent[i].temp || currentTemp;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        const denom = (n * sumXX) - (sumX * sumX);
        if (denom !== 0) {
            const slopePerSec = ((n * sumXY) - (sumX * sumY)) / denom;
            slopePer10s = slopePerSec * 10.0;
        }
    } else {
        slopePer10s = currentTemp > 42.0 ? 0.65 : (currentTemp > 39.0 ? 0.35 : 0.05);
    }

    // 2. Calculate FPS Stability Variance
    let fpsVariance = 0.0;
    if (Array.isArray(telemetryHistory) && telemetryHistory.length >= 2) {
        const fpsList = telemetryHistory.map(p => p.fps || 60);
        const meanFps = fpsList.reduce((a, b) => a + b, 0) / fpsList.length;
        const sumSq = fpsList.reduce((acc, v) => acc + Math.pow(v - meanFps, 2), 0);
        fpsVariance = sumSq / fpsList.length;
    }

    // 3. Evaluate Risk Scoring
    let riskLevel = "LOW";
    let recommendation = "Thermals and framerate stable. Optimal performance.";
    let isIncidentTriggered = false;

    const isHighTemp = currentTemp >= 43.5 || (currentTemp >= 41.5 && slopePer10s >= 0.60 && currentFps < 55);
    const isMedTemp = (currentTemp >= 39.0 && slopePer10s >= 0.30) || fpsVariance >= 20.0 || currentFps < 52;

    if (isHighTemp) {
        riskLevel = "HIGH";
        recommendation = `High thermal ramp (+${slopePer10s.toFixed(2)}°C/10s)! Lower refresh rate to 90Hz or reduce graphics.`;
        isIncidentTriggered = true;
    } else if (isMedTemp) {
        riskLevel = "MEDIUM";
        recommendation = `Temperature rising (+${slopePer10s.toFixed(2)}°C/10s). Monitor load and close background tasks.`;
    }

    res.json({
        riskLevel,
        currentTemp,
        tempSlopePer10Sec: parseFloat(slopePer10s.toFixed(2)),
        currentFps,
        fpsVariance: parseFloat(fpsVariance.toFixed(2)),
        batteryPercent,
        batteryDrainRate,
        recommendation,
        isIncidentTriggered,
        timestamp: Date.now()
    });
});

// ==========================================
// 3. GAMING SESSIONS API ENDPOINTS
// ==========================================

// GET all sessions
app.get('/api/sessions', (req, res) => {
    res.json({
        success: true,
        count: sessions.length,
        sessions: sessions
    });
});

// POST save a new session
app.post('/api/sessions', (req, res) => {
    const {
        gameLabel = "COSMIC_VOID_v1.0",
        durationSeconds = 60,
        efficiencyPct = 90.0,
        throttlingEventsAvoided = 1,
        avgFps = 60.0,
        minFps = 55.0,
        peakTemp = 36.0,
        avgTemp = 35.0,
        grade = "S",
        gradeScore = 95,
        summaryFeedback = "Session recorded successfully.",
        samplesJson = "[]"
    } = req.body;

    const newSession = {
        id: sessions.length + 1,
        gameLabel,
        startTimeMs: Date.now() - (durationSeconds * 1000),
        endTimeMs: Date.now(),
        durationSeconds,
        sessionTimeFormatted: durationSeconds >= 60 ? `${Math.floor(durationSeconds / 60)} min` : `${durationSeconds}s`,
        efficiencyPct: parseFloat(efficiencyPct.toFixed(1)),
        throttlingEventsAvoided,
        avgFps: parseFloat(avgFps.toFixed(1)),
        minFps: parseFloat(minFps.toFixed(1)),
        onePercentLowFps: parseFloat((minFps + 2).toFixed(1)),
        peakTemp: parseFloat(peakTemp.toFixed(1)),
        avgTemp: parseFloat(avgTemp.toFixed(1)),
        grade,
        gradeScore,
        summaryFeedback,
        samplesJson
    };

    sessions.unshift(newSession);

    res.status(201).json({
        success: true,
        message: "Session saved successfully",
        sessionId: newSession.id,
        session: newSession
    });
});

// GET session by ID
app.get('/api/sessions/:id', (req, res) => {
    const session = sessions.find(s => s.id === parseInt(req.params.id, 10));
    if (!session) {
        return res.status(404).json({ success: false, message: "Session not found" });
    }
    res.json({ success: true, session });
});

// GET aggregated stats
app.get('/api/stats', (req, res) => {
    const totalSessions = sessions.length;
    const totalDurationSeconds = sessions.reduce((a, b) => a + b.durationSeconds, 0);
    const avgEfficiency = totalSessions > 0 ? (sessions.reduce((a, b) => a + b.efficiencyPct, 0) / totalSessions) : 0;
    const totalEventsAvoided = sessions.reduce((a, b) => a + b.throttlingEventsAvoided, 0);

    res.json({
        totalSessions,
        totalPlaytimeMinutes: Math.round(totalDurationSeconds / 60),
        avgEfficiencyPct: parseFloat(avgEfficiency.toFixed(1)),
        totalThrottlingEventsAvoided: totalEventsAvoided,
        hardwarePlatform: "iQOO Monster Performance Architecture"
    });
});

// Start Server
app.listen(PORT, () => {
    console.log(`🛡️ Thermal & Performance Guardian Backend API running on port ${PORT}`);
});
