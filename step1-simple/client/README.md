# Chat client

A terminal client for the step 1 chat.

## Running

- Run via Gradle:
    ```bash
    ./gradlew :step1-simple:client:run --args="--name alice" --console=plain
    ```

    Here `--console=plain` keeps Gradle's progress bar from overwriting incoming messages.

- Build it once and run it directly:

    ```bash
    ./gradlew :step1-simple:client:installDist
    step1-simple/client/build/install/client/bin/client --name alice
    ```

## Options

| Option   | Default     |                            |
|----------|-------------|----------------------------|
| `--name` | required    | the name you connect under |
| `--host` | `localhost` | where to look              |
| `--port` | `8080`      | which port                 |

A name is 3 to 32 characters of lowercase letters, digits and single `_` or `-`
separators, starting with a letter.

## Sending

```
@bob hello there
```

The recipient is the word after `@`; the body is the rest of the line, verbatim, so it
may contain spaces, punctuation and further `@`s without escaping. Addressing yourself
is allowed and arrives once.

A body may be up to 8 KiB and 100 lines, and may not carry control characters other than
tab and newline. Bodies are checked before they are sent.

Blank lines are ignored.

## Commands

A line beginning with `/` is an instruction for the client.

| Command |                               |
|---------|-------------------------------|
| `/help` | list what can be typed        |
| `/exit` | close the connection and exit |

## What you see

```
nobody else is here                              you are the first
here: bob, carol                                 who was connected when you arrived
bob joined
bob left
bob: hi                                          a message to you
-> bob: hi                                       your own, echoed back
! unknown_recipient: 'dave' is not connected     a refusal
```

Lines beginning with `!` are refusals, either from the server or from the client before
sending. The connection survives all of them.

Nothing is stored. A message to a name that is not connected is refused rather than
held, and there is no history, so anything sent before you arrived is gone.

## Exiting

| Code |                                                                   |
|------|-------------------------------------------------------------------|
| `0`  | the connection closed cleanly — `Ctrl-D`, or the server went away |
| `1`  | the name was refused, or nothing answered at the host and port    |

There is no reconnect: when the socket closes, the client exits.
