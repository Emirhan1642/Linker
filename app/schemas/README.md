# Room Database Schemas

This directory contains exported Room database schemas for version tracking and migration validation.

## Schema Files

Room automatically exports schema JSON files when `exportSchema = true` is set in the `@Database` annotation.

### Current Version: 5

**Migration History:**
- Version 1: Initial schema (base tables)
- Version 2 → 3: Added privacy features (isPrivate, followRequestSent)
- Version 3 → 4: Added follow list visibility (hideFollowLists)
- Version 4 → 5: Added message features (reply, reactions, status, read receipts)

## Usage

These schema files are used for:
1. **Migration Testing**: Validate migrations don't break existing data
2. **Version Control**: Track database structure changes over time
3. **Documentation**: Reference for database structure at each version

## Testing Migrations

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LinkerDatabase::class.java
    )

    @Test
    fun migrate2To3() {
        // Test migration logic
    }
}
```

## Notes

- Schema files are auto-generated during build
- Keep these files in version control
- Review schema changes during code review
- Test migrations before releasing to production
