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

## Testing a release locally

Build and run the Docker image locally before pushing a tag:

```bash
docker build -t ranking-table .

# Pipe input
echo "Lions 3, Snakes 3" | docker run --rm -i ranking-table

# File input
docker run --rm -v $(pwd)/results.txt:/app/results.txt ranking-table results.txt

# File input with output file
docker run --rm \
  -v $(pwd)/results.txt:/app/results.txt \
  -v $(pwd)/rankings.txt:/app/rankings.txt \
  ranking-table results.txt --output-file rankings.txt
```

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
