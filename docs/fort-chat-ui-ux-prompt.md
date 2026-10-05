# Fort Chat UI/UX direction

## Reusable prompt

Redesign Fort Chat's existing Android Jetpack Compose screens as a polished, privacy-first messenger. Keep the established ice-blue and white palette, deep-navy text, royal-blue actions, softly translucent glass cards, bright borders, rounded corners, and restrained shadows. Keep layouts clean, readable, and responsive in light and dark themes.

Make the Chats screen immediately useful: keep Quiet Presence manually chosen and explain who can see it and when it expires. When there are no conversations, show a calm, honest empty state with the actions “Scan an invite” and “Create an invite”, plus “Find by Fort ID (Knock First)” as a quieter option. Hide filters and the new-chat floating action when there are no conversations. When a filter has no matches, explain that state and provide “Show all conversations”.

Make Knock First requests easy to understand and review. Show the real request details, keep each request pending until the recipient explicitly decides, and preserve accept, decline, and block actions. Calls remain available only for accepted contacts. Never auto-accept a request.

Use the app's actual bottom navigation: Chats, Circles, Requests, You. Preserve real callbacks and existing privacy/security behavior. Support English and Bangla, honor system light/dark themes, keep primary touch targets at least 48dp, and maintain readable contrast and TalkBack labels.

Do not invent contacts, messages, inviter identities, verification states, encryption states, or success messages. Show only information the app actually knows, and label unavailable features honestly.
