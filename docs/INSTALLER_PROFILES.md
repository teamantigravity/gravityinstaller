# Installer Profiles

Installer Profiles allow users to save and reuse specific installation configurations (backend, permissions, target user, etc.) and map them to specific applications.

## Data Model (`InstallerProfile`)

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | Unique identifier (UUID). |
| `name` | `String` | User-friendly name for the profile. |
| `installerPackageName` | `String?` | Custom installer package name to spoof. |
| `preferredBackend` | `String?` | "Root", "Shizuku", or "Default". |
| `replaceExisting` | `Boolean?` | Corresponds to `-r` flag. |
| `allowTest` | `Boolean?` | Corresponds to `-t` flag. |
| `requestDowngrade` | `Boolean?` | Corresponds to `-d` flag. |
| `grantAllPermissions` | `Boolean?` | Corresponds to `-g` flag. |
| `bypassLowTargetSdk` | `Boolean?` | Bypass Android 14+ restrictions on old apps. |
| `allUsers` | `Boolean?` | Install for all users. |
| `targetUserId` | `Int?` | Install for a specific User ID (e.g., Work Profile). |

## Management Logic

### `ProfileManager`
Handles serialization and deserialization of profiles and app-to-profile mappings using `kotlinx.serialization`.

- `parseProfiles(json: String?)`: Returns `List<InstallerProfile>`.
- `serializeProfiles(profiles: List<InstallerProfile>)`: Returns JSON `String`.
- `parseMapping(json: String?)`: Returns `Map<String, String>` (Package Name -> Profile ID).

### `SettingViewModel`
Provides methods for UI interaction:
- `saveProfile(profile)`: Adds or updates a profile in DataStore.
- `deleteProfile(profileId)`: Removes a profile and cleans up associated mappings.
- `setAppProfileMapping(packageName, profileId)`: Links an app to a profile.

## Application Logic

### `InstallViewModel`
- `applyProfile(profile)`: Updates the current DataStore preferences with values from the selected profile just before starting an installation.

## Future Work (UI)
- [ ] Create a "Profiles" section in Settings to manage (CRUD) profiles.
- [ ] Implement a Profile Picker in the Install Dialog's Advanced tab.
- [ ] Implement "Smart Pick" logic to automatically select a profile based on the package name during parsing.
