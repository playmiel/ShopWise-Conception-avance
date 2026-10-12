-- V5 : tracabilite des ventes (US 5) : utilisateur authentifie ayant enregistre la vente.
-- Colonne facultative : les ventes enregistrees avant la securisation n'ont pas d'auteur connu.
ALTER TABLE sales ADD COLUMN created_by VARCHAR(50);