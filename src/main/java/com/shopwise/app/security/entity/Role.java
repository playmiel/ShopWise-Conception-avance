package com.shopwise.app.security.entity;

/**
 * Roles applicatifs :
 * - ADMIN    : gere le catalogue (creation, modification, suppression) et peut tout faire ;
 * - MERCHANT : commercant / vendeur, enregistre les ventes et consulte ;
 * - VIEWER   : consultation seule (ex. comptable).
 */
public enum Role {
	ADMIN, MERCHANT, VIEWER
}