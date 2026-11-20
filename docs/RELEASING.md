# Releasing AnimatorFX

## What is published

The manually triggered **Release** workflow uses the same Sonatype Central Publisher and GPG
approach as [GemsFX](https://github.com/dlsc-software-consulting-gmbh/GemsFX).

Maven Central receives these coordinates, all at the requested release version:

- `com.dlsc.animatorfx:animatorfx-parent` (the runtime's parent POM)
- `com.dlsc.animatorfx:animatorfx-model`
- `com.dlsc.animatorfx:animatorfx-player`

The runtime JARs have signed source and Javadoc JARs, license/notice files, and publication metadata.
The editor, importer and sample application are not deployed to Central. "Runtime" here means
the embeddable JavaFX libraries, not a standalone JDK distribution.

A GitHub release tagged `v<version>` receives native editor archives for the Linux, Windows and
macOS runner architectures (recorded in each filename). Each includes a linked Java runtime;
users do not need to install Java. The application images are unsigned and not notarized, and
are not installers. Extract the archive before launching. CI artifacts provide the same images
for development builds.

Archives include the project license and notice, a `licenses/` directory with runtime dependency
license texts and a dependency-to-license index, and the linked JDK's own `legal/` directory
inside the application image. Packaging fails if a dependency's declared license cannot be
collected. Review these notices when changing dependencies; third-party licenses are not replaced
by AnimatorFX's Apache license.

The release also starts the **jDeploy** workflow at the new version tag. It attaches editor
installers for Windows x64, Linux x64 and macOS (Intel and Apple Silicon) to that same GitHub
release, and maintains jDeploy's update metadata in a separate `jdeploy` release. Do not delete
that metadata release or tag: installed applications use it to discover updates.

jDeploy downloads an appropriate Java runtime and platform-specific JavaFX, including media.
Users do not need Java installed, but need network access for installation and updates.
The portable editor payload retains dependency JARs, service-provider metadata and license notices;
it does not ship the build machine's JavaFX native libraries. The existing self-contained native
archives remain available as an alternative.

Application binaries and update metadata are hosted **only on GitHub**, not npm. `package.json`
is jDeploy's configuration format and is marked private to prevent accidental npm publication.
jDeploy's CLI itself is fetched from npm; this does not publish the application there.

## One-time GitHub and Sonatype setup

1. Enable GitHub Actions and create an environment named `maven-central`. Restrict it to the
   default branch and, preferably, require maintainer approval.
2. Use a Sonatype Central Portal account authorized to publish the `com.dlsc.animatorfx` namespace.
   Generate a **publisher user token** in the Portal (not the account login password).
3. Configure the following environment secrets. Their names match GemsFX:

   | Secret | Value |
   |---|---|
   | `PUBLISHER_PORTAL_USERNAME` | Central Portal token username |
   | `PUBLISHER_PORTAL_TOKEN` | Central Portal token password |
   | `GPG_PRIVATE_KEY` | ASCII-armored private signing key, including signing subkeys if used |
   | `GPG_PASSPHRASE` | Passphrase for that key |

4. Publish the corresponding public signing key to a public keyserver accessible to Central.
   Keep the private key and passphrase out of Git, logs and issue reports.
5. Allow the Release workflow to use `contents: write` and `actions: write` for its GitHub-release
   job, and jDeploy to use `contents: write`. The generated `GITHUB_TOKEN` creates the tag/release
   and dispatches the jDeploy workflow; no personal access token or npm credentials are required.
6. Enable private vulnerability reporting in the repository's Security settings. Configure branch
   protection to require all three Build jobs and review before merging.

`actions/setup-java` imports the GPG key and generates the `central` Maven server entry.
The release command maps the Portal secrets to `MAVEN_USERNAME` / `MAVEN_CENTRAL_TOKEN` and the
signing passphrase to `MAVEN_GPG_PASSPHRASE`, just as GemsFX does. No committed `settings.xml`
containing credentials is needed. PR builds have no publishing secrets or write permissions.

The repository must be public so jDeploy installers can download releases and updates.
Keep `package.json`'s `jdeploy.javaVersion` and `jdeploy.javafxVersion` aligned with
`editor.java.release` and `javafx.editor.version` in the parent POM when upgrading Java or JavaFX.
The jDeploy action and CLI are pinned together in `.github/workflows/jdeploy.yml`.

## Cutting a release

1. Merge the changes to the default branch and check that Build is green. Review compatibility
   and release notes. Try the downloaded editor and sound on the target platforms.
2. Under **Actions > Release > Run workflow**, select the default branch and provide a new stable
   version such as `1.0.0`. Versions must be `major.minor.patch`, with major at least 1 for
   macOS `jpackage` compatibility. The tag, including its `v` prefix, must be at most 16 characters
   for the jDeploy action. Existing tags and snapshot/prerelease strings are rejected.
3. All three platforms build/test and package the exact selected commit. POM versions are set
   in the runner checkout, including parent and inter-module references. No version commit or
   branch push is made.
4. Approve the `maven-central` environment if required. The publish job deploys **only** the parent,
   model and player with `-Drelease=true`. Central validates and automatically publishes the signed
   bundle; the workflow waits for publication to complete.
5. Only after Central succeeds does the workflow tag the selected source commit and create the
   GitHub release with the application archives and generated notes.
6. Release explicitly dispatches **jDeploy** at that exact tag. A release created with
   `GITHUB_TOKEN` does not itself trigger another release-event workflow, so this dispatch is
   intentional. Check that the separate jDeploy run succeeds before announcing the installers.
   The action sets the package version from the tag and preserves generated release notes while
   adding installer links. It never publishes moving-branch snapshots.

The source branch remains at its development version. A release tag identifies the source commit;
to reproduce a release locally, run the same `versions:set` command with the tagged version first.
No release has been published merely by adding these files.

## Local checks without publishing

With JDK 26 as `JAVA_HOME`:

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Drelease=true -Dgpg.skip=true \
  -pl :animatorfx-parent,:animatorfx-model,:animatorfx-player verify
```

The second command builds the runtime's source and Javadoc attachments without signing or deploying.
It is **not** a publication: signatures and Central validation are required for an actual release.
Normal builds do not activate any publication plugin.

To build and inspect the editor's jDeploy payload without publishing:

```bash
./mvnw -B -ntp -Pjdeploy -pl animatorfx-editor -am package -DskipTests
npx --yes jdeploy@6.1.12 package
```

Run these commands from the repository root, with Node.js 22 installed. Only skip tests after
the portable suite has passed. Maven writes the executable editor JAR, dependency JARs, icon
and license notices to `animatorfx-editor/target/jdeploy/`; the second command assembles
`jdeploy-bundle/`. Neither command publishes a release. Normal Linux CI also builds the Maven
payload after its tests have passed.

## Recovering from a failed release

Central versions are immutable. If deployment failed validation before publication, correct the
cause and retry. If Central succeeded but GitHub-release creation failed, rerun only the failed
GitHub job; do **not** republish the already-published Maven version. That job downloads the
application artifacts from the same workflow run (retained for 14 days).

If GitHub already created the tag/release before an upload failed, download the archives from that
run and attach them to the existing release instead of starting another release with the same
version. Never overwrite an existing version with different binaries.

If GitHub-release creation succeeded but jDeploy dispatch or installer generation failed, fix the
cause and rerun **only jDeploy** at the existing tag, for example:

```bash
gh workflow run jdeploy.yml --ref v1.0.0
```

Do not rerun Central publication. Dispatching jDeploy at a branch or without an existing published
stable release is rejected. jDeploy runs are serialized to protect their shared update metadata.
