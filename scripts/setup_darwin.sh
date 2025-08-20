#!/usr/bin/env bash
set -euo pipefail

### Configurable Versions
JAVA_VERSION="11"
MAVEN_VERSION="3.9.9"
DOCKER_VERSION="25.0.3"
BREW_INSTALL_URL="https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"

### Printable constants
FANCY_PATTERN="***********************************"

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

install_homebrew() {
    echo "Installing Homebrew"
    if ! command_exists brew; then
        echo "Installing Homebrew..."
        /bin/bash -c "$(curl -fsSL ${BREW_INSTALL_URL})"
    else
        echo "Homebrew already installed."
    fi
}

install_java() {
    echo "Installing Java"
    if ! command_exists java || ! java -version 2>&1 | grep -q "version \"${JAVA_VERSION}"; then
        echo "Installing Java ${JAVA_VERSION}..."
        brew install openjdk@${JAVA_VERSION}
        sudo ln -sfn /usr/local/opt/openjdk@${JAVA_VERSION}/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-${JAVA_VERSION}.jdk
    else
        echo "Java ${JAVA_VERSION} already installed."
    fi
}

install_maven() {
    echo "Installing Maven"
    if ! command_exists mvn; then
        echo "Installing Maven ${MAVEN_VERSION}..."
        brew install maven
    else
        echo "Maven already installed."
    fi
}

install_docker() {
    echo "Installing Docker"
    if ! command_exists docker; then
        echo "Installing Docker..."
        brew install --cask docker
    else
        echo "Docker already installed."
    fi
}

# Setup execution starts here.

echo "----- Starting to setup MacOS -----"

# Whenever a new setup step is added, do not forget to increment this number
# otherwise the logging is going to look weird.
NUM_STEPS="4"

declare -i step=1

echo "${FANCY_PATTERN} Step ${step}/${NUM_STEPS} ${FANCY_PATTERN}"
install_homebrew
((step++))
echo ""

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