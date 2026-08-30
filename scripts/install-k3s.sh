#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run this script as root on the Oracle Linux/Ubuntu VM." >&2
  exit 1
fi

command -v curl >/dev/null 2>&1 || {
  echo "curl is required before installing k3s." >&2
  exit 1
}

export INSTALL_K3S_CHANNEL=${INSTALL_K3S_CHANNEL:-stable}
curl -sfL https://get.k3s.io | sh -s - server \
  --disable traefik \
  --write-kubeconfig-mode 600

systemctl is-active --quiet k3s
k3s kubectl wait --for=condition=Ready node --all --timeout=180s
k3s kubectl get nodes -o wide

echo "k3s is ready. Kubeconfig: /etc/rancher/k3s/k3s.yaml"
