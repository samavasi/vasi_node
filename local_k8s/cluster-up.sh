#!/bin/bash

# Requires docker (tested with Docker desktop 4.4.2 (73305) on MacOS 11.2.3)
# Requires kind to be installed on local workstation and added to path
# tested with `kind version 0.12.0` and explicit k8s cluster version 1.20 (see kind-config.yaml)
# also adjusted Docker desktop for kind https://kind.sigs.k8s.io/docs/user/quick-start/#settings-for-docker-desktop
# using kubectx/kubens for context/namespace switching so some commands in this script might require this

set -o errexit

# Get kubectl
if [ ! -f /usr/local/bin/kubectl ]; then
  curl -LO "https://dl.k8s.io/release/v1.20.15/bin/darwin/amd64/kubectl"
  chmod 700 kubectl
  mv kubectl /usr/local/bin
fi

# Get helm
if [ ! -f /usr/local/bin/helm ]; then
  curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
  chmod 700 get_helm.sh
  ./get_helm.sh
  rm ./get_helm.sh
fi

# create registry container unless it already exists
reg_name='kind-registry'
if [ "$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)" != 'true' ]; then
  docker run \
    -d --restart=always -p "127.0.0.1:5001:5000" --name "${reg_name}" \
    registry:2
fi

if [ "$(kubectx kind-k8s-vasi 2>/dev/null || true)" != 'Switched to context "kind-k8s-vasi".' ]; then
  kind create cluster --name k8s-vasi --config kind-config.yaml
fi

# Check kubectl/kind cluster connectivity
kubectx kind-k8s-vasi
kubectl version

# connect the registry to the cluster network if not already connected
if [ "$(docker inspect -f='{{json .NetworkSettings.Networks.kind}}' "${reg_name}")" = 'null' ]; then
  docker network connect "kind" "${reg_name}"
fi

# Document the local registry & # Ingress setup
kubectl apply -f boot_manifests/

kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

# Deploy logging stack in k8s cluster
helm upgrade --namespace monitor --create-namespace --install elasticsearch --wait ./monitor_logging_charts/elasticsearch -f ./monitor_logging_charts/elasticsearch/values-local.yaml
helm upgrade --namespace monitor --create-namespace --install kibana --wait ./monitor_logging_charts/kibana -f ./monitor_logging_charts/kibana/values.yaml
helm upgrade --namespace monitor --create-namespace --install filebeat --wait ./monitor_logging_charts/filebeat -f ./monitor_logging_charts/filebeat/values.yaml
# Deploy monitoring stack in k8s cluster
helm upgrade --namespace monitor --create-namespace --install promstack --wait ./monitor_logging_charts/kube-prometheus-stack -f ./monitor_logging_charts/kube-prometheus-stack/values.yaml
helm upgrade --namespace monitor --create-namespace --install blackbox-exp --wait ./monitor_logging_charts/prometheus-blackbox-exporter -f ./monitor_logging_charts/prometheus-blackbox-exporter/values.yaml
kubectl apply -f ./monitor_logging_charts/extradashboards
