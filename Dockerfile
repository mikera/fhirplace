FROM ubuntu:14.04
MAINTAINER Nikolay Ryzhikov <niquola@gmail.com>

RUN apt-get -qq update
RUN apt-get install -qq -y software-properties-common curl
RUN apt-get -qqy install git build-essential
RUN add-apt-repository ppa:webupd8team/java && apt-get -qq update
RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -qq -y oracle-java7-installer
RUN useradd -d /home/fhir -m -s /bin/bash fhir && echo "fhir:fhir"|chpasswd && adduser fhir sudo

RUN echo 'fhir ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers

USER fhir
ENV HOME /home/fhir
RUN cd /home/fhir && mkdir -p /home/fhir/bin && curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > /home/fhir/bin/lein && chmod a+x /home/fhir/bin/lein
RUN /home/fhir/bin/lein
env PATH /home/fhir/bin:$PATH

RUN sudo apt-get install -qq -y tmux zsh
RUN cd ~/ && git clone https://github.com/niquola/dotfiles
RUN cd ~/dotfiles && bash install.sh

RUN cd ~/ && git clone https://github.com/fhirbase/fhirplace.git
RUN cd ~/fhirplace && git pull origin master && echo 'v0.0.0'
RUN cd ~/fhirplace && git submodule init && git submodule update
RUN cd ~/fhirplace && lein deps
RUN cd ~/fhirplace && cp lein-env.tpl .lein-env && lein javac
RUN cd ~/fhirplace && cp dev/production.clj dev/user.clj

EXPOSE 3000

CMD cd ~/fhirplace && env FHIRPLACE_SUBNAME="//$DB_PORT_5432_TCP_ADDR:$DB_PORT_5432_TCP_PORT/fhirbase" lein repl