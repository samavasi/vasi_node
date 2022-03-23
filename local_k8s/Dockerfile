FROM jenkins/jenkins:lts-jdk11

# Switch to root as the base image switch to jenkins user
USER root

# Download docker-cli and install it
RUN curl -o docker-ce-cli.deb https://download.docker.com/linux/debian/dists/stretch/pool/stable/amd64/docker-ce-cli_19.03.8~3-0~debian-stretch_amd64.deb && \
    dpkg -i docker-ce-cli.deb && \
    rm docker-ce-cli.deb

RUN apt-get update \
      && apt-get install -y sudo \
      && rm -rf /var/lib/apt/lists/*
RUN echo "jenkins ALL=NOPASSWD: ALL" >> /etc/sudoers

WORKDIR /tmp

RUN curl -LO "https://dl.k8s.io/release/v1.20.15/bin/linux/amd64/kubectl" && \
    chmod 777 kubectl && \
    mv kubectl /usr/bin

RUN curl -LO "https://get.helm.sh/helm-v3.8.1-linux-amd64.tar.gz" && \
    tar -zxvf helm-v3.8.1-linux-amd64.tar.gz && \
    chmod 777 linux-amd64/helm && \
    mv linux-amd64/helm /usr/bin

USER jenkins

#Install some helm/k8s test/security tools
RUN helm plugin install https://github.com/instrumenta/helm-kubeval

RUN sudo apt update -y && \
    sudo apt install -y python3 python3-pip && \
    sudo pip install checkov
