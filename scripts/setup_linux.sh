#!/usr/bin/env bash
set -euo pipefail

### Configurable Versions
JAVA_VERSION="11"
MAVEN_VERSION="3.9.9"
DOCKER_VERSION="25.0.3"

### Printable constants
FANCY_PATTERN="***********************************"

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

install_java() {
    echo "Installing Java"
    if ! command_exists java || ! java -version 2>&1 | grep -q "version \"${JAVA_VERSION}"; then
        echo "Installing Java ${JAVA_VERSION}..."
        sudo apt-get update -y
        sudo apt-get install -y openjdk-${JAVA_VERSION}-jdk
    else
        echo "Java ${JAVA_VERSION} already installed."
    fi
}

install_maven() {
    echo "Installing Maven"
    if ! command_exists mvn; then
        echo "Installing Maven ${MAVEN_VERSION}..."
        sudo apt-get update -y
        sudo apt-get install -y maven
    else
        echo "Maven already installed."
    fi
}

install_docker() {
    echo "Installing Docker"
    if ! command_exists docker; then
        echo "Installing Docker..."
        sudo apt-get update -y
        sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        echo \
          "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] \
          https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
          sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
        sudo apt-get update -y
        sudo apt-get install -y docker-ce docker-ce-cli containerd.io
    else
        echo "Docker already installed."
    fi
}

# Setup execution starts here.

echo "----- Starting to setup Linux -----"

# Whenever a new setup step is added, do not forget to increment this number
# otherwise the logging is going to look weird.
NUM_STEPS="3"

declare -i step=1

echo "${FANCY_PATTERN} Step ${step}/${NUM_STEPS} ${FANCY_PATTERN}"
install_java
((step++))
echo ""

echo "${FANCY_PATTERN} Step ${step}/${NUM_STEPS} ${FANCY_PATTERN}"
install_maven
((step++))
echo ""

echo "${FANCY_PATTERN} Step ${step}/${NUM_STEPS} ${FANCY_PATTERN}"
install_docker
((step++))
echo ""
