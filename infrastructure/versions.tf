# Infrastructure placeholder — BMW Driving Coach
#
# This file declares the required Terraform providers for the Azure deployment.
# It is intentionally minimal until the infrastructure design is finalised.
#
# Required Azure resources (TBD):
#   - azurerm_resource_group
#   - azurerm_app_service_plan + azurerm_linux_web_app (backend Node.js)
#   - azurerm_postgresql_flexible_server
#   - azurerm_storage_account + azurerm_storage_container (telemetry blobs)
#
# State backend:
#   Configured via environment variables at `terraform init` time (see TF-03).
#   Storage account must use Azure AD auth only (SEC-06).

terraform {
  required_version = ">= 1.9.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # TODO(infra): enable Azure Storage backend once provisioned.
  # backend "azurerm" {}
}

provider "azurerm" {
  features {}
  # Credentials are supplied via ARM_* environment variables set by azure/login
  # OIDC action in CI (CD-AD-13). No client_secret is ever stored.
  use_oidc = true
}
