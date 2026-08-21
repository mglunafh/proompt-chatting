# Step 2 dependency map

## Nodes

task     W-01    Build scaffolding
task     W-02    Postgres and Compose
task     W-03    Schema and migrations
task     W-04    Database test harness
task     W-05    Server entry point and health
task     W-06    Protocol module
task     W-07    Validation
task     W-08    Users and passwords
task     W-09    Sessions store
task     W-10    Invites and account creation
task     W-11    Bootstrap admin
task     W-12    REST authentication
task     W-13    Auth routes
task     W-14    Credential rate limiting
task     W-15    Account disable
task     W-16    Connection registry
task     W-17    Upgrade and read loop
task     W-18    Heartbeat and expiry
task     W-19    Revocation reach
task     W-20    Presence edges and grace
task     W-21    Presence snapshot
task     W-22    Last seen
task     W-23    Conversation and message store
task     W-24    Send path and fan-out
task     W-25    REST read routes
task     W-26    Client token store
task     W-27    Client account commands
task     W-28    Client connection and reconnect
task     W-29    Client rendering
task     W-30    Client commands

feature  MSG-01  Direct messages
feature  MSG-02  Conversation list
feature  MSG-03  Message history
feature  MSG-04  Live connection
feature  AUTH-01 Opaque session tokens
feature  AUTH-02 Registration
feature  AUTH-03 Admin bootstrapping
feature  AUTH-04 Login
feature  AUTH-05 Request authentication
feature  AUTH-06 Logout and revocation
feature  AUTH-10 Multi-session
feature  AUTH-11 Token lifetime
feature  USR-04  Presence
feature  USR-05  Presence snapshot
feature  USR-07  Last seen
feature  MOD-06  Ban
feature  SEC-02  Credential rate limiting
feature  SEC-05  Name and label rules
feature  SEC-06  Password rules
feature  SEC-07  Message body limits
feature  SEC-08  Transport caps
feature  SEC-09  Client-side sanitization
feature  OPS-01  Health endpoint
feature  ST2-01  Client token store
feature  ST2-02  Client account commands
feature  ST2-03  Client commands

## Order

W-01 -> W-02, W-06, W-07, W-26
W-02 -> W-03
W-03 -> W-04, W-23
W-04 -> W-05, W-08, W-09, W-23
W-05 -> W-11, W-12, W-17, W-22
W-06 -> W-16, W-17, W-28
W-07 -> W-08, W-13, W-24
W-08 -> W-10, W-11, W-13, W-15, W-23
W-09 -> W-12, W-13, W-16, W-18, W-22
W-10 -> W-13, W-15
W-11 -> W-15
W-12 -> W-13, W-15, W-17, W-25
W-13 -> W-14, W-19, W-27
W-15 -> W-19
W-16 -> W-17, W-20, W-21, W-24
W-17 -> W-18, W-19, W-20, W-21, W-24, W-28
W-20 -> W-21, W-22
W-21 -> W-29
W-23 -> W-24, W-25
W-24 -> W-30
W-25 -> W-30
W-26 -> W-27, W-28
W-28 -> W-29
W-29 -> W-30

## Coverage

MSG-01  <- W-23, W-24
MSG-02  <- W-23, W-25
MSG-03  <- W-23, W-25
MSG-04  <- W-06, W-17, W-18, W-28
AUTH-01 <- W-09, W-16
AUTH-02 <- W-10, W-13, W-27
AUTH-03 <- W-11
AUTH-04 <- W-08, W-13, W-27
AUTH-05 <- W-12, W-17
AUTH-06 <- W-13, W-19
AUTH-10 <- W-09
AUTH-11 <- W-09, W-18
USR-04  <- W-16, W-20
USR-05  <- W-16, W-21
USR-07  <- W-22
MOD-06  <- W-15, W-19
SEC-02  <- W-14
SEC-05  <- W-07
SEC-06  <- W-07
SEC-07  <- W-06, W-07
SEC-08  <- W-06
SEC-09  <- W-29
OPS-01  <- W-05
ST2-01  <- W-26
ST2-02  <- W-27
ST2-03  <- W-30

## Sources

todo.md
features.md
