# Release Process

## Overview

Releases are triggered by pushing a version tag. The workflow:

1. Builds a Docker image and pushes it to GitHub Container Registry (ghcr.io)
2. Extracts the assembly JAR from the Docker builder stage (layer cache reuse — no recompilation)
3. Creates a GitHub Release with the JAR attached as a downloadable asset

## Creating a release

```bash
git tag v1.0.0
git push --tags
```

The workflow runs automatically. Once complete:

- Docker image: `ghcr.io/decarb/ranking-table:v1.0.0`
- JAR: attached to the GitHub Release as `ranking-table.jar`

## Running a released JAR

Requires only a JRE (Java 21+) — no Docker or scala-cli needed:

```bash
java -jar ranking-table.jar             # interactive prompt
java -jar ranking-table.jar results.txt # file input
```

## Running a released image

No scala-cli or JDK required — just Docker.

```bash
# Interactive prompt (-it allocates a pseudo-TTY, triggering the prompt loop)
docker run --rm -it ghcr.io/decarb/ranking-table:v1.0.0

# Pipe input (simplest for small inputs)
echo "Lions 3, Snakes 3
Tarantulas 1, FC Awesome 0" | docker run --rm -i ghcr.io/decarb/ranking-table:v1.0.0

# File input (-v mounts a local file into the container at /app/results.txt)
docker run --rm -v $(pwd)/results.txt:/app/results.txt ghcr.io/decarb/ranking-table:v1.0.0 results.txt

# File input with output file (mount both local files into the container)
docker run --rm \
  -v $(pwd)/results.txt:/app/results.txt \
  -v $(pwd)/rankings.txt:/app/rankings.txt \
  ghcr.io/decarb/ranking-table:v1.0.0 results.txt --output-file rankings.txt
```

## Tag protection

**Solo repo:** No action needed. External contributors submit PRs from forks and have no push access, so only you can create tags.

**If you add collaborators with Write access:** they can push tags by default. To keep release control with admins only, create a tag ruleset:

1. Settings → Rules → Rulesets → New ruleset
2. Target: `refs/tags/v*`
3. Restrict creations to: Admins only

Write-access collaborators can still push branches and merge PRs — they just can't trigger releases.

## Testing a release

Push a pre-release tag to trigger the full workflow without creating an official release:

```bash
git tag v0.0.0-rc1
git push --tags
```

Verify the image, JAR asset, and release on GitHub, then delete both the tag and release:

```bash
git tag -d v0.0.0-rc1
git push --delete origin v0.0.0-rc1
gh release delete v0.0.0-rc1 --yes
```

To test the Dockerfile locally before pushing:

```bash
docker build -t ranking-table .

# Interactive prompt
docker run --rm -it ranking-table

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
