package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Utilisateur;
import com.fasodev.gestionscolaire.repositories.UtilisateurRepository;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Logique métier liée à l'authentification Desktop.
 * Tout se passe en LOCAL (SQLite), aucune dépendance réseau.
 */
public class AuthService {

    private final UtilisateurRepository utilisateurRepository = new UtilisateurRepository();

    /**
     * Vrai si aucun utilisateur n'existe encore.
     * Sert à savoir s'il faut afficher l'assistant de configuration
     * initiale (création du 1er compte Directeur) au lieu du login.
     */
    public boolean estPremierLancement() {
        return utilisateurRepository.compterUtilisateurs() == 0;
    }

    /**
     * Tente une connexion. Retourne l'utilisateur si succès, sinon null.
     */
    public Utilisateur connecter(String nomUtilisateur, String motDePasse) {

        if (nomUtilisateur == null || nomUtilisateur.isBlank()
                || motDePasse == null || motDePasse.isBlank()) {
            return null;
        }

        Utilisateur utilisateur = utilisateurRepository.findByNomUtilisateur(nomUtilisateur.trim());

        if (utilisateur == null || !utilisateur.isActif()) {
            return null;
        }

        boolean motDePasseValide = BCrypt.checkpw(motDePasse, utilisateur.getPasswordHash());

        if (!motDePasseValide) {
            return null;
        }

        SessionManager.connecter(utilisateur);
        return utilisateur;
    }

    /**
     * Crée le tout premier compte (toujours ADMIN = Directeur).
     * Utilisé uniquement lors de l'assistant de configuration initiale.
     */
    public Utilisateur creerPremierCompteAdmin(String nomUtilisateur, String motDePasse) {

        if (!estPremierLancement()) {
            throw new IllegalStateException(
                "Un compte existe déjà, impossible de recréer le premier compte."
            );
        }

        validerNomUtilisateur(nomUtilisateur);
        validerMotDePasse(motDePasse);

        String hash = BCrypt.hashpw(motDePasse, BCrypt.gensalt());
        Utilisateur admin = new Utilisateur(nomUtilisateur.trim(), hash, "ADMIN");

        Utilisateur saved = utilisateurRepository.save(admin);
        SessionManager.connecter(saved);
        return saved;
    }

    /**
     * Crée un compte Secrétaire (USER). Réservé aux actions du Directeur,
     * appelé plus tard depuis un écran de gestion des utilisateurs.
     */
    public Utilisateur creerCompteSecretaire(String nomUtilisateur, String motDePasse) {

        validerNomUtilisateur(nomUtilisateur);
        validerMotDePasse(motDePasse);

        if (utilisateurRepository.findByNomUtilisateur(nomUtilisateur.trim()) != null) {
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà.");
        }

        String hash = BCrypt.hashpw(motDePasse, BCrypt.gensalt());
        Utilisateur user = new Utilisateur(nomUtilisateur.trim(), hash, "USER");

        return utilisateurRepository.save(user);
    }

    public java.util.List<Utilisateur> listerUtilisateurs() {
        return utilisateurRepository.findAll();
    }

    /**
     * Active ou désactive un compte. Garde-fous : on ne peut pas désactiver
     * son propre compte, ni le dernier Directeur actif (sinon plus personne
     * ne pourrait gérer l'application).
     */
    public void changerActivation(int utilisateurId, boolean actif) {
        Utilisateur cible = utilisateurRepository.findById(utilisateurId);
        if (cible == null) {
            throw new IllegalArgumentException("Utilisateur introuvable.");
        }
        if (!actif) {
            Utilisateur courant = SessionManager.getUtilisateurConnecte();
            if (courant != null && courant.getId() == utilisateurId) {
                throw new IllegalStateException("Tu ne peux pas désactiver ton propre compte.");
            }
            if (cible.isAdmin() && cible.isActif() && utilisateurRepository.compterAdminsActifs() <= 1) {
                throw new IllegalStateException("Impossible de désactiver le dernier Directeur actif.");
            }
        }
        utilisateurRepository.changerActivation(utilisateurId, actif);
    }

    public void reinitialiserMotDePasse(int utilisateurId, String nouveauMotDePasse) {
        validerMotDePasse(nouveauMotDePasse);
        if (utilisateurRepository.findById(utilisateurId) == null) {
            throw new IllegalArgumentException("Utilisateur introuvable.");
        }
        utilisateurRepository.changerMotDePasse(
            utilisateurId, BCrypt.hashpw(nouveauMotDePasse, BCrypt.gensalt()));
    }

    private void validerNomUtilisateur(String nomUtilisateur) {
        if (nomUtilisateur == null || nomUtilisateur.trim().length() < 3) {
            throw new IllegalArgumentException(
                "Le nom d'utilisateur doit contenir au moins 3 caractères."
            );
        }
    }

    private void validerMotDePasse(String motDePasse) {
        if (motDePasse == null || motDePasse.length() < 6) {
            throw new IllegalArgumentException(
                "Le mot de passe doit contenir au moins 6 caractères."
            );
        }
    }
}
