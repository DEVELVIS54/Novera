package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.AccesParent;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.ParentIdentifiantGenere;
import com.fasodev.gestionscolaire.repositories.AccesParentRepository;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service de gestion des identifiants parents.
 *
 * Responsabilités :
 * - Générer des identifiants uniques (format ETABLISSEMENT-CLASSE-NUMERO)
 * - Générer des mots de passe 6 caractères (éviter confusions visuelles)
 * - Créer et stocker en base
 * - Retourner le mot de passe en clair pour affichage/impression
 */
public class ParentIdentifiantService {

    private static final Random RANDOM = new Random();

    /**
     * Caractères valides pour le mot de passe parent (évite 0/O, 1/I, l, confusion)
     * Garder des majuscules et chiffres facilement lisibles à l'impression
     */
    private static final String CHARSET_MDPIDENTIFIANT =
        "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; // Pas 0/O/1/I/L

    /**
     * Génère un identifiant unique pour un étudiant.
     * Format: ETABLISSEMENT-CLASSE-NUMERO
     *
     * Exemple: LSJK-6A-0042 (LSJK = initiales école, 6A = classe, 0042 = numéro étudiant zero-padded)
     *
     * @param etudiant         l'étudiant pour lequel générer
     * @param sigleEcole       sigle de l'établissement (ex: "LSJK")
     * @return identifiant généré (format validé unique)
     */
    public static String genererIdentifiant(Etudiant etudiant, String sigleEcole) {
        String classeNom = etudiant.getClasseNom();
        if (classeNom == null || classeNom.isEmpty()) {
            classeNom = "XX";
        }

        // Truncate classe name to 2 chars max, uppercase
        classeNom = (classeNom.length() > 2 ? classeNom.substring(0, 2) : classeNom).toUpperCase();

        // Zero-padded student ID (4 digits)
        String numeroEtudiant = String.format("%04d", etudiant.getId() % 10000);

        String identifiant = sigleEcole.toUpperCase() + "-" + classeNom + "-" + numeroEtudiant;

        // Vérifier l'unicité en base ; si collision (rarissime), incrémenter le numéro
        int tentatives = 0;
        while (AccesParentRepository.obtenirParIdentifiant(identifiant) != null && tentatives < 100) {
            int num = Integer.parseInt(numeroEtudiant) + tentatives + 1;
            numeroEtudiant = String.format("%04d", num % 10000);
            identifiant = sigleEcole.toUpperCase() + "-" + classeNom + "-" + numeroEtudiant;
            tentatives++;
        }

        return identifiant;
    }

    /**
     * Génère un mot de passe parent : 6 caractères aléatoires, lisibles à l'impression
     * @return mot de passe en clair (6 caractères)
     */
    public static String genererMotDePasse() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(CHARSET_MDPIDENTIFIANT.charAt(RANDOM.nextInt(CHARSET_MDPIDENTIFIANT.length())));
        }
        return sb.toString();
    }

    /**
     * Crée un nouvel accès parent pour un étudiant.
     *
     * @param etudiant         l'étudiant
     * @param sigleEcole       sigle de l'établissement
     * @return ParentIdentifiantGenere contenant le mdp en clair + accès stocké en base
     */
    public static ParentIdentifiantGenere creerIdentifiant(Etudiant etudiant, String sigleEcole) {
        // Générer identifiant + mot de passe
        String identifiant = genererIdentifiant(etudiant, sigleEcole);
        String motDePasseClair = genererMotDePasse();
        String passwordHash = BCrypt.hashpw(motDePasseClair, BCrypt.gensalt());

        // Créer l'objet AccesParent et le sauver en base
        AccesParent acces = new AccesParent(etudiant.getId(), identifiant, passwordHash);
        AccesParentRepository.creer(acces);

        // Retourner le DTO avec le mot de passe en clair (temporairement)
        return new ParentIdentifiantGenere(
            acces.getId(),
            etudiant.getId(),
            etudiant.getNomComplet(),
            etudiant.getClasseNom(),
            identifiant,
            motDePasseClair
        );
    }

    /**
     * Génère les identifiants pour tous les étudiants actifs d'une classe (ou toutes les classes).
     * Évite les doublons : si un étudiant a déjà un accès, il est ignoré (ou peut être regénéré si demandé).
     *
     * @param classeId         classe cible (0 = toutes les classes)
     * @param sigleEcole       sigle de l'établissement
     * @param forceRegenerer   si true, supprime les anciens accès et en génère de nouveaux
     * @return liste des ParentIdentifiantGenere (y compris anciens si !forceRegenerer)
     */
    public static List<ParentIdentifiantGenere> genererPourClasse(
        int classeId,
        String sigleEcole,
        boolean forceRegenerer
    ) {
        List<ParentIdentifiantGenere> resultats = new ArrayList<>();

        // Récupérer les étudiants (actifs seulement)
        List<Etudiant> etudiants = (classeId > 0)
            ? EtudiantRepository.obtenirParClasse(classeId)
            : EtudiantRepository.obtenirTous();

        for (Etudiant etudiant : etudiants) {
            if (!etudiant.isActif()) {
                continue;
            }

            // Vérifier s'il a déjà un accès
            List<AccesParent> accesExistants = AccesParentRepository.obtenirParEtudiant(etudiant.getId());

            if (forceRegenerer && !accesExistants.isEmpty()) {
                // Supprimer les anciens
                for (AccesParent ancien : accesExistants) {
                    AccesParentRepository.supprimer(ancien.getId());
                }
            }

            // Générer un nouvel accès
            if (forceRegenerer || accesExistants.isEmpty()) {
                ParentIdentifiantGenere genere = creerIdentifiant(etudiant, sigleEcole);
                resultats.add(genere);
            } else {
                // Retourner l'accès existant (SANS le mdp en clair, car on ne le connaît pas)
                // => ici on crée un "DTO partiel" juste avec les infos de base
                AccesParent ancien = accesExistants.get(0);
                ParentIdentifiantGenere dto = new ParentIdentifiantGenere(
                    ancien.getId(),
                    ancien.getEtudiantId(),
                    etudiant.getNomComplet(),
                    etudiant.getClasseNom(),
                    ancien.getIdentifiant(),
                    "***" // Indique qu'on n'a pas le mdp en clair (déjà hashé)
                );
                resultats.add(dto);
            }
        }

        return resultats;
    }

    /**
     * Obtient tous les accès parents actifs (pour impression en masse).
     */
    public static List<ParentIdentifiantGenere> obtenirAccesActifs() {
        List<ParentIdentifiantGenere> resultats = new ArrayList<>();
        List<AccesParent> tous = AccesParentRepository.obtenirTousActifs();

        for (AccesParent acces : tous) {
            Etudiant etudiant = EtudiantRepository.obtenirParId(acces.getEtudiantId());
            if (etudiant != null) {
                ParentIdentifiantGenere dto = new ParentIdentifiantGenere(
                    acces.getId(),
                    acces.getEtudiantId(),
                    etudiant.getNomComplet(),
                    etudiant.getClasseNom(),
                    acces.getIdentifiant(),
                    "***" // mdp pas connu
                );
                resultats.add(dto);
            }
        }

        return resultats;
    }

    /**
     * Valide un identifiant + mdp parent (appel du portail web parent).
     * @return l'étudiant associé si authentification réussie, null sinon
     */
    public static Etudiant validerAcces(String identifiant, String motDePasseClair) {
        AccesParent acces = AccesParentRepository.obtenirParIdentifiant(identifiant);

        if (acces == null || !acces.isActif()) {
            return null;
        }

        // Vérifier le mot de passe
        if (!BCrypt.checkpw(motDePasseClair, acces.getPasswordHash())) {
            return null;
        }

        // Mettre à jour la date de première utilisation (si c'était la 1ère)
        if (acces.getDateUtilisationPremiere() == null) {
            acces.setDateUtilisationPremiere(java.time.LocalDateTime.now());
            AccesParentRepository.mettreAJour(acces);
        }

        return EtudiantRepository.obtenirParId(acces.getEtudiantId());
    }
}
