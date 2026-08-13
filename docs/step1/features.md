# Step 1 features

## From the feature set

- **MSG-04 Live connection** — one WebSocket per client, carrying typed client and server frames defined in a module both sides depend on and encoded as JSON. A rejected client frame comes back as a typed error frame rather than a close. Reduced: no protocol version check, no heartbeat, and no reconnect — the client exits when the socket closes.
- **MSG-01 Direct messages** — a send frame names one connected recipient; the server validates the body and writes it to that recipient's socket and back to the sender. Reduced: nothing is stored, so a send to a name that is not connected is refused rather than held, and there is no history, no server-assigned ID and no offline delivery.
- **USR-04 Presence** — who is currently connected, from the in-process connection registry. A join is broadcast to every other connected client on insert and a leave on removal. Reduced: one connection per name, so the edges are the connection itself; no grace period on leave, and no `last_seen_at`.
- **USR-05 Presence snapshot** — the roster of connected names is pushed as the first frame after the upgrade, captured by the same registry operation that adds the socket. Reduced: no digest and no reconciliation, since there is no heartbeat to carry one.
- **SEC-07 Message body limits** — size and line caps on the send frame, and rejection of the C0 and C1 control characters that carry terminal escape sequences. Reduced: the caps are constants in the shared module rather than configuration.
- **SEC-09 Client-side sanitization** — the client renders received text inertly, escaping what it prints rather than letting the socket drive the terminal. Reduced: applies to message bodies and connected names, the only untrusted text a client renders.

## Step-local

- **ST1-01 Connect name** — identity is a name the client passes as a command-line argument and the server takes at the upgrade. It must pass the same character rules as a message body and be unique among connected clients; a duplicate is refused at the upgrade. The name lives for the length of the connection and is the address a direct message is sent to.
1