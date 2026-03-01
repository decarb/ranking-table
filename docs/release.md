# Release Process

Releases are triggered by pushing a version tag. The workflow builds a Docker image, pushes it to
GitHub Container Registry, and creates a GitHub Release with the assembly JAR attached as a
downloadable asset.

## Creating a release

```bash
git tag v1.0.0
git push --tags
```

Once the workflow completes:

- **Docker image:** `ghcr.io/decarb/ranking-table:v1.0.0` (also tagged `latest`)
- **JAR:** attached to the GitHub Release as `ranking-table.jar`

## Dockerfile stages

The Dockerfile uses three stages:

| Stage        | Base                            | Purpose                                                 |
| ------------ | ------------------------------- | ------------------------------------------------------- |
| `builder`    | `virtuslab/scala-cli`           | Compiles source and produces the assembly JAR           |
| `jar-export` | `scratch`                       | Holds only the JAR for clean CI extraction              |
| *(default)*  | `eclipse-temurin:21-jre-alpine` | Minimal runtime image for `docker build` / `docker run` |

The `jar-export` stage exists because the `builder` stage leaves a Bloop Unix domain socket on
disk. BuildKit's local exporter cannot handle socket files and will fail when extracting build
output. Copying only the JAR into an empty (`scratch`) image gives the exporter a clean, portable
artifact. `docker build -t ranking-table .` always targets the default stage — local usage is
unaffected by the intermediate stage.

## Running a released Docker image

No Scala CLI or JDK required — just Docker.

```bash
# Interactive prompt
docker run --rm -it ghcr.io/decarb/ranking-table:v1.0.0

# Pipe input
echo "Lions 3, Snakes 3
Tarantulas 1, FC Awesome 0" | docker run --rm -i ghcr.io/decarb/ranking-table:v1.0.0

# File input
docker run --rm \
  -v $(pwd)/results.txt:/app/results.txt \
  ghcr.io/decarb/ranking-table:v1.0.0 results.txt

# File input with output file
docker run --rm \
  -v $(pwd)/results.txt:/app/results.txt \
  -v $(pwd)/rankings.txt:/app/rankings.txt \
  ghcr.io/decarb/ranking-table:v1.0.0 results.txt --output-file rankings.txt
```

## Running a released JAR

Requires Java 21+ — no Docker or Scala CLI needed.

```bash
java -jar ranking-table.jar
java -jar ranking-table.jar results.txt
java -jar ranking-table.jar results.txt --output-file rankings.txt
```

## Smoke testing a local build

Build a fresh image from the current source, then run through all input modes and verify exit
codes before merging or tagging a release.

```bash
docker build -t ranking-table .
```

**Piped input** — exit 0, correct ranking printed to stdout:

```bash
printf "Lions 3, Snakes 3\nTarantulas 1, FC Awesome 0\nLions 1, FC Awesome 1\nTarantulas 3, Snakes 1\nLions 4, Grouches 0\n" \
  | docker run --rm -i ranking-table
# 1. Tarantulas, 6 pts
# 2. Lions, 5 pts
# 3. FC Awesome, 1 pt
# 3. Snakes, 1 pt
# 5. Grouches, 0 pts
```

**File input** — exit 0, same output:

```bash
docker run --rm -v $(pwd)/results.txt:/app/results.txt ranking-table results.txt
```

**File input with output file** — exit 0, content written to mounted file:

```bash
touch rankings.txt
docker run --rm \
  -v $(pwd)/results.txt:/app/results.txt \
  -v $(pwd)/rankings.txt:/app/rankings.txt \
  ranking-table results.txt --output-file rankings.txt
cat rankings.txt
```

**Help flag** — exit 0, usage printed:

```bash
docker run --rm ranking-table --help
```

**Invalid input (file)** — exit 1, clean `Error: ...` line on stderr, no stack trace:

```bash
echo "not a valid line" | docker run --rm -i ranking-table 2>&1
# Error: Expected 'Team score, Team score' but got: 'not a valid line'
```

All six checks must pass before merging or tagging a release.

To trigger the full release workflow without creating an official release, push a pre-release tag:

```bash
git tag v0.0.0-rc1
git push --tags
```

Verify the Docker image and JAR asset on GitHub, then delete the test tag and release:

```bash
git tag -d v0.0.0-rc1
git push --delete origin v0.0.0-rc1
gh release delete v0.0.0-rc1 --yes
```

## Tag protection

By default, anyone with repository write access can push a `v*` tag. To restrict release creation
to administrators:

1. Settings → Rules → Rulesets → New ruleset
2. Target: `refs/tags/v*`
3. Restrict creations to: Admins only

Write-access collaborators can still push branches and merge PRs — only tag creation (and
therefore release triggering) is restricted.
