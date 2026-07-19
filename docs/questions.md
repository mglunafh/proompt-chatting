## Questions to define tech tack, architecture, implementation plan

### Plaintext messenger
- messenger
  ```
  I want to build a messenger using kotlin. Currently trhe focus should be on the
  server side, so let's assume the client would be jvm-based console app.
  What tech stack would you suggest?
  ```
- for small communities
  ```
  I see this messenger as a communication tool for small community, around 10-50 people,
  so I would not plan to add high availability, high load and global scale stuff

  Adjust tech stack suggestion and feature list having this in mind
  ```

- tech stack
- feature list
  ```text
  Suggest a feature list for the messenger. Use stable feature ID references across feature lists.
  Group the features in a subsection by areas: Core messaging, Users & presence,
    Group / community management, Notifications & UX, Search & content, Security, Admin / ops,
    Transport resilience, Attachments, Nice-to-have.
  Then group the same feature list in a subsection by difficulty:
    trivial, easy, medium, hard, very hard, ignore.
  ```

- Difficulty assessment
  ```text
  let's walk through the difficulty assessment of the features.
  Pick every feature from 'Trivial' subsection and briefly explain why it is trivial
  ```

- implementation plan

### E2EE messenger

- messenger
  ```text
  Ok, we discussed the implementation of a plaintext messenger in Kotlin.
  Now let's think about the implementation of e2ee messenger.

  Describe the Kotlin-based tech stack of e2ee messenger for small community 
  ```

### Client implementation pluggable to both messengers

```text
What if i want to reuse the 'frontend' side of client code for both plaintext and e2ee messenger?
There could be console-based app, TUI-based app, probably GUI-based app
```

```text
show a high-level gradle structure of modules regarding frontends
and describe some notes regarding implementation plan
```

### Feature set

```text
It feels like a lot features to be added to the messenger
I would like to work on features sets: for example i'd argue the simplest version
of a messenger is allowed to be launched locally on the same machine and do not have
authentication at all.
```

```text
I feel like we have a lot of features and it's getting hard to keep track of them
Features can probably be split into categories:
- Important features, vital parts of a milestone
- Features which are unlocked in a milestone, but can be implemented any time later;
    may block other non-important features
```
