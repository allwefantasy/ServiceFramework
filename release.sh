mvn clean deploy -DskipTests -Pjetty-9 -Pweb-include-jetty-9 -P release-sign-artifacts
#https://oss.sonatype.org/#stagingRepositories
#mvn versions:set -DnewVersion={version}