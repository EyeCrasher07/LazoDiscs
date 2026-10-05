# Bundled third-party resources

The project's existing GPL-3.0-only license remains in the archive's root
`LICENSE`. Third-party components retain their upstream terms; this directory
does not relicense them or select new terms.

The audio dependency JARs do not all include their own license files. The
supplemental upstream copies here are included in every shaded release under
`META-INF/third-party/`. `PROVENANCE.json` records exact sources, versions,
extraction boundaries and SHA-256 hashes. These files are local build inputs;
building does not download legal texts.

| Bundled component | Preserved upstream material |
| --- | --- |
| LavaPlayer, lava-common and lavaplayer-natives 2.2.7 | Complete Apache-2.0 license from the upstream 2.2.7 tag. |
| YouTube-source v2/common, commit `2be8e542d3f6f178e048dca565892684c2e40177` | Complete upstream MIT license, including devoxin's copyright notice. |
| Nanojson 1.7 | Verbatim source copyright/license header. The [release commit's README](https://github.com/mmastrac/nanojson/blob/2601212d3baa84aeae527dff098310aae530684b/README.md) describes dual MIT/Apache licensing; the preserved source header refers to Apache-2.0, whose complete text is also bundled. |
| JSON-java 20260719 | Complete upstream public-domain declaration. |
| Base64 2.3.9 | Upstream public-domain declaration remains in `META-INF/maven/net.iharder/base64/pom.xml`. |
| Jackson annotations 2.21, core/databind 2.21.7 | JAR-supplied Apache licenses and all notices, including Jackson-core credits and its FastDoubleParser/Schubfach third-party license files. |
| HttpClient 4.5.14, HttpCore 4.4.16 and Commons Logging 1.2 | JAR-supplied Apache licenses and component copyright notices. |
| Jsoup 1.16.1 | JAR-supplied MIT license and copyright notice. |
| Rhino and rhino-engine 1.7.15.1 | Complete JAR-supplied MPL-2.0 license and Rhino notices, including bundled third-party attributions. |

`gradle/third-party-notices.gradle` appends colliding `META-INF/LICENSE`,
`LICENSE.txt`, `NOTICE` and `NOTICE.txt` resources instead of discarding all but
the first. Other legal files keep their original paths. Class/resource duplicate
exclusion, relocation and dependencies are unchanged.

Every project's `check` task runs `verifyBundledNotices` against the **final**
release JAR (after Fabric remapping). It verifies that every legal resource in
each bundled dependency and every supplemental file is present in full. Keep
the provenance current when updating dependencies; this packaging check is not
a substitute for reviewing newly introduced upstream terms.
