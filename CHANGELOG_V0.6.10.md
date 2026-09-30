# RedForge v0.6.10

## Timer feedback
- Fixed rest-timer completion feedback so the foreground service stays alive for the full feedback sequence.
- Completion now reliably plays the configured multi-tone signal instead of stopping after the first tone.
- Kept the existing vibration pattern and timer sound/vibration settings.

## Split creation
- Predefined templates now automatically use their template name.
- Push / Pull / Legs, Upper / Lower, and Full Body no longer ask the user to type the split name.
- Custom splits still ask for a user-defined name.

## Progress UI
- Primary progress-screen content now explicitly inherits the theme's semantic `onSurface` color to prevent black text on dark surfaces.
- Exercise progress top bar colors are explicitly tied to the dark theme surface/on-surface colors.

## Typography
- Refined the existing system-sans typography hierarchy.
- Added consistent headline, body-small, and label-medium slots.
- Tightened large-display letter spacing for a cleaner metrics presentation.

