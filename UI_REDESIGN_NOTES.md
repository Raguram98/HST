# AudioPulse UI refresh

This update refreshes the existing Android Views/XML interface without migrating the project to Jetpack Compose or changing the Room/database/session-tracking architecture.

## What changed

- Reworked the Sessions screen with a clearer header, more spacious session cards, connection-type chips, active/completed badges, and a polished empty state.
- Reworked the Insights screen with an all-time listening hero card, wireless/wired breakdown cards, today's listening time, and the most-used device card.
- Introduced a coordinated light/dark palette and updated the bottom navigation with custom vector icons.
- Updated session card colors and duration labels while retaining the existing view-binding IDs and session data flow.
- Removed tab changes from the activity back stack and retained the selected tab across activity recreation.

## Implementation

The app continues to use Kotlin, XML layouts, ViewBinding, Material Components, fragments, RecyclerView, and the existing ViewModel/repository/database layers. No new Gradle dependencies are required for this UI refresh.

Open the project in Android Studio and run the `app` configuration on an Android 8.0+ device/emulator.
