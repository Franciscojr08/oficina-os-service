#!/usr/bin/env bash
# Protege a branch main no GitHub: só entra por pull request, com os checks do pipeline verdes e a
# branch atualizada com a main. Rodar uma vez, por quem administra o repositório (requer gh logado).
#
# Uso: scripts/proteger-main.sh [owner/repo]
set -euo pipefail

REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"

# Nomes dos jobs do .github/workflows/pipeline.yml (o "Deploy EKS" só roda depois do merge)
gh api --method PUT "repos/$REPO/branches/main/protection" --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Qualidade", "Imagem", "Manifestos"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON

echo "main protegida em $REPO: PR obrigatório com Qualidade, Imagem e Manifestos aprovados."
