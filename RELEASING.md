# Releasing Adhar Kit to Maven Central

This document describes how to publish Adhar Kit artifacts to Maven Central via Sonatype OSSRH.

## Prerequisites

### 1. Sonatype OSSRH Account

Register at https://central.sonatype.com/ and claim the `com.adhar.kit` namespace.

### 2. GPG Key for Artifact Signing

```bash
# Generate a GPG key pair
gpg --gen-key

# List keys to find your key ID
gpg --list-keys

# Publish your public key to a key server
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
gpg --keyserver keys.openpgp.org --send-keys <YOUR_KEY_ID>
```

### 3. Maven Settings (~/.m2/settings.xml)

```xml
<settings>
  <servers>
    <!-- Sonatype OSSRH credentials -->
    <server>
      <id>central</id>
      <username>${env.MAVEN_CENTRAL_USERNAME}</username>
      <password>${env.MAVEN_CENTRAL_PASSWORD}</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.keyname>${env.GPG_KEY_ID}</gpg.keyname>
        <gpg.passphrase>${env.GPG_PASSPHRASE}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

### 4. Environment Variables

```bash
export MAVEN_CENTRAL_USERNAME=your-sonatype-username
export MAVEN_CENTRAL_PASSWORD=your-sonatype-token
export GPG_KEY_ID=your-gpg-key-id
export GPG_PASSPHRASE=your-gpg-passphrase
```

## Release Process

### Option 1: Maven Release Plugin (Recommended)

```bash
# Dry run to verify everything is ready
mvn release:prepare -DdryRun=true

# Prepare the release (updates versions, creates tag)
mvn release:prepare -Prelease

# Perform the release (builds, signs, deploys to staging)
mvn release:perform -Prelease

# If something goes wrong, rollback
mvn release:rollback
```

This will:
1. Verify no uncommitted changes exist
2. Remove `-SNAPSHOT` from version (e.g., `1.0.0-SNAPSHOT` -> `1.0.0`)
3. Run tests
4. Commit the release version
5. Create a Git tag `v1.0.0`
6. Bump to next development version (`1.0.1-SNAPSHOT`)
7. Build, sign, and deploy all artifacts to Maven Central staging

### Option 2: Manual Release

```bash
# 1. Set the release version
mvn versions:set -DnewVersion=1.0.0
mvn versions:commit

# 2. Build, test, and deploy
mvn clean deploy -Prelease -Dgpg.passphrase=${GPG_PASSPHRASE}

# 3. Tag the release
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0

# 4. Set next development version
mvn versions:set -DnewVersion=1.0.1-SNAPSHOT
mvn versions:commit
git add -A && git commit -m "chore: prepare for next development cycle"
git push
```

### Option 3: GitHub Actions (CI/CD)

The repository includes a GitHub Actions workflow at `.github/workflows/release.yml` that automates the release process. Trigger it by creating a GitHub Release.

## Post-Release Verification

After release, verify artifacts are available:

```bash
# Check Maven Central (may take 10-30 minutes to sync)
curl -s "https://repo1.maven.org/maven2/com/adhar/kit/adhar-kit-bom/1.0.0/" | head -20

# Verify in a test project
mvn dependency:resolve -Dartifact=com.adhar.kit:adhar-kit-starter:1.0.0
```

## Module Publishing Order

The Maven reactor handles ordering automatically, but for reference:

1. `adhar-kit-bom` (POM only - no JAR)
2. `adhar-kit-parent` (POM only)
3. `adhar-kit-commons` (foundation)
4. All other modules (dependency order managed by Maven)
5. `adhar-kit-starter` (aggregator - depends on all others)

## Artifacts Published Per Module

Each module publishes:
- `<module>-<version>.jar` - compiled classes
- `<module>-<version>-sources.jar` - source code (required by Maven Central)
- `<module>-<version>-javadoc.jar` - JavaDoc (required by Maven Central)
- `<module>-<version>.pom` - POM descriptor
- `<module>-<version>.jar.asc` - GPG signature (required by Maven Central)

## Troubleshooting

| Issue | Solution |
|-------|----------|
| GPG signing fails | Ensure `gpg-agent` is running: `gpg-agent --daemon` |
| Sonatype auth fails | Verify credentials in `~/.m2/settings.xml` match your Sonatype token |
| Javadoc generation fails | Build with `-Ddoclint=none` or fix JavaDoc errors |
| Version already exists | Maven Central does not allow overwriting. Increment version. |
| Staging repository not found | Login to https://central.sonatype.com/ and manually close/release |
