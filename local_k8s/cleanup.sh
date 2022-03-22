#!/bin/bash

set -o errexit

reg_name='kind-registry'

kind delete cluster --name k8s-vasi
rm -f /usr/local/bin/kubectl
rm -f /usr/local/bin/helm

if [ "$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)" == 'true' ]; then
  docker rm -f "${reg_name}"
fi
