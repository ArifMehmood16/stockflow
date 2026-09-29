# Third-party assets

The six technology SVG files in `prototype/assets/` are unmodified icons from [Devicon v2.17.0](https://github.com/devicons/devicon/tree/v2.17.0/icons): Java, Spring, PostgreSQL, Redis, React and NGINX. Downloaded from the corresponding `icons/<name>/<name>-original.svg` paths. The original MIT copyright and permission notice is retained in [DEVICON-LICENSE.txt](prototype/assets/DEVICON-LICENSE.txt).

These logos identify technologies in the proposed teaching architecture. They do not imply endorsement or that those services run inside this browser prototype. Product names and marks remain the property of their respective owners; the upstream licence does not replace brand-specific trademark policies. StockFlow itself has not selected a project licence.

Assets are served locally with an explicit allowlist; the page makes no external image requests. SVGs were checked for script, foreignObject, embedded image/use references and event-handler attributes before inclusion. No image-generation model was used to redraw brand marks.

## Java and catalog dependencies

- [pgJDBC 42.7.13](https://jdbc.postgresql.org/): BSD 2-clause licence, including its packaged notices. The downloaded jar also retains its bundled dependency notices under META-INF/licenses.
- [univocity-parsers 2.9.1](https://github.com/uniVocity/univocity-parsers): Apache License 2.0. Used for streaming TSV/CSV parsing and writing.
- [Open Food Facts](https://openfoodfacts.github.io/openfoodfacts-server/api/): database ODbL, individual contents DbCL. Attribution and source/snapshot/checksum metadata accompany the local cache. Images are excluded. Public dataset contents are not committed to this repository; publication of a derived database must respect its applicable licence obligations.

Java dependencies are fetched by Build.java with SHA-256 checks. No jar or downloaded catalog is tracked in Git. Temurin is the verified local test JDK; its distribution retains its own notices in the ignored installation.

## Java inventory service build

The service uses Apache Maven 3.9.11 (Apache-2.0), Spring Boot 4.0.8 / Spring Framework (Apache-2.0), and their managed runtime/test dependencies, including Apache Tomcat (Apache-2.0), HikariCP (Apache-2.0), Jackson (Apache-2.0), SLF4J (MIT), Logback (EPL-1.0 / LGPL-2.1), and JUnit (EPL-2.0). pgJDBC remains pinned as above. Dependencies are downloaded, not vendored in Git; retain upstream notices with binary distributions. Maven's archive SHA-512 is pinned in tools/MavenBuild.java. This dependency notice does not select a licence for StockFlow itself. A complete release SBOM/licence review remains a release task.
