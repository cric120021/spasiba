const { WebSocketServer } = require("ws");
const http = require("http");
const crypto = require("crypto");

const PORT = process.env.SOCKET_PORT || 8080;
const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ status: "Eunoia Socket Server Running", timestamp: Date.now() }));
});

const wss = new WebSocketServer({ server });

// Map of sessionId -> Set<WebSocket>
const sessions = new Map();
// Map of sessionId -> sequence number
const sessionSequences = new Map();
// Map of sessionId -> authoritative state snapshot
const sessionStates = new Map();

function getNextSeq(sessionId) {
  const current = sessionSequences.get(sessionId) || 0;
  const next = current + 1;
  sessionSequences.set(sessionId, next);
  return next;
}

function broadcastToRoom(sessionId, senderWs, eventName, payload, senderDeviceId) {
  const room = sessions.get(sessionId);
  if (!room) return;

  const serverTime = Date.now();
  const sequence = getNextSeq(sessionId);
  const eventMessage = JSON.stringify({
    eventId: crypto.randomUUID(),
    sessionId,
    senderDeviceId,
    eventType: eventName,
    sequence,
    serverTime,
    payload
  });

  room.forEach((client) => {
    if (client !== senderWs && client.readyState === 1) { // 1 = OPEN
      client.send(eventMessage);
    }
  });
}

wss.on("connection", (ws, req) => {
  console.log(`[SocketServer] New client connection from ${req.socket.remoteAddress}`);
  let currentSessionId = null;
  let currentDeviceId = null;

  ws.on("message", (rawMessage) => {
    try {
      const msg = JSON.parse(rawMessage.toString());
      const { sessionId, senderDeviceId, eventType, payload } = msg;

      if (!sessionId || !senderDeviceId) return;

      currentSessionId = sessionId;
      currentDeviceId = senderDeviceId;

      if (!sessions.has(sessionId)) {
        sessions.set(sessionId, new Set());
      }
      sessions.get(sessionId).add(ws);

      const serverTime = Date.now();
      const sequence = getNextSeq(sessionId);

      switch (eventType) {
        case "joinSession": {
          console.log(`[SocketServer] Device ${senderDeviceId} joined session ${sessionId}`);
          const snapshot = sessionStates.get(sessionId) || {
            sessionId,
            currentSong: payload?.currentSong || null,
            queue: payload?.queue || [],
            queueIndex: payload?.queueIndex || 0,
            positionMs: payload?.positionMs || 0,
            isPlaying: payload?.isPlaying || false,
            lastUpdatedAt: serverTime,
            controllerDeviceId: senderDeviceId
          };
          sessionStates.set(sessionId, snapshot);

          ws.send(JSON.stringify({
            eventId: crypto.randomUUID(),
            sessionId,
            senderDeviceId: "SERVER",
            eventType: "sessionSnapshot",
            sequence,
            serverTime,
            payload: snapshot
          }));

          broadcastToRoom(sessionId, ws, "partnerReconnected", { deviceId: senderDeviceId }, senderDeviceId);
          break;
        }

        case "play":
        case "pause":
        case "seek":
        case "next":
        case "previous":
        case "trackChanged":
        case "queueUpdated":
        case "chatMessage":
        case "reaction":
        case "heartbeat": {
          // Update session snapshot state if state payload provided
          if (payload && (eventType === "play" || eventType === "pause" || eventType === "seek" || eventType === "trackChanged")) {
            const current = sessionStates.get(sessionId) || {};
            sessionStates.set(sessionId, {
              ...current,
              ...payload,
              lastUpdatedAt: serverTime,
              controllerDeviceId: senderDeviceId
            });
          }

          // Broadcast event to partner in the room
          broadcastToRoom(sessionId, ws, eventType, payload || {}, senderDeviceId);

          // Send sequence ack back to sender
          ws.send(JSON.stringify({
            eventId: msg.eventId || crypto.randomUUID(),
            sessionId,
            senderDeviceId: "SERVER",
            eventType: "ack",
            sequence,
            serverTime,
            payload: { originalEventId: msg.eventId }
          }));
          break;
        }

        default:
          broadcastToRoom(sessionId, ws, eventType, payload || {}, senderDeviceId);
          break;
      }
    } catch (err) {
      console.error("[SocketServer] Error handling message:", err);
    }
  });

  ws.on("close", () => {
    if (currentSessionId && currentDeviceId) {
      console.log(`[SocketServer] Device ${currentDeviceId} disconnected from session ${currentSessionId}`);
      const room = sessions.get(currentSessionId);
      if (room) {
        room.delete(ws);
        if (room.size === 0) {
          sessions.delete(currentSessionId);
        } else {
          broadcastToRoom(currentSessionId, null, "partnerLeft", { deviceId: currentDeviceId }, currentDeviceId);
        }
      }
    }
  });
});

server.listen(PORT, () => {
  console.log(`🎧 Eunoia Socket Server running on port ${PORT}`);
});
