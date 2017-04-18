FROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/tv-broadcast
ADD tv-broadcast-0.0.8.jar /tmp/tv-broadcast/
ADD keystore.jks /

ENTRYPOINT java  -Dtest.rooms=true -jar /tmp/tv-broadcast/tv-broadcast-0.0.8.jar

