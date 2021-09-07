FROM stabilaprotocol/stabila-gradle

RUN set -o errexit -o nounset \
#  Download and build java-stabila
    && echo "git clone" \
    && git clone https://github.com/stabilaprotocol/java-stabila.git \
    && cd java-stabila \
    && gradle build

# Change work directory
WORKDIR /java-stabila

# open port 18888
EXPOSE 18888

