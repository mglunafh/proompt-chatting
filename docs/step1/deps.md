# Step 1 dependency map

## Nodes

task     W-01    Build scaffolding
task     W-02    Protocol module
task     W-03    Validation
task     W-04    Connection registry
task     W-05    Endpoint and upgrade
task     W-06    Roster and edges
task     W-07    Message routing
task     W-08    Client
task     W-09    Client rendering

feature  MSG-01  Direct messages
feature  MSG-04  Live connection
feature  USR-04  Presence
feature  USR-05  Presence snapshot
feature  SEC-07  Message body limits
feature  SEC-09  Client-side sanitization
feature  ST1-01  Connect name

## Order

W-01 -> W-02
W-02 -> W-03, W-04, W-08
W-03 -> W-05, W-07
W-04 -> W-05, W-06
W-05 -> W-06, W-07, W-08
W-08 -> W-09

## Coverage

MSG-01 <- W-07
MSG-04 <- W-02, W-05, W-08
USR-04 <- W-04, W-06
USR-05 <- W-04, W-06
SEC-07 <- W-02, W-03
SEC-09 <- W-09
ST1-01 <- W-03, W-04, W-05

## Sources

todo.md
features.md
