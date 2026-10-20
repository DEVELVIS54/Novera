package com.fasodev.gestionscolaire.tools;

import com.fasodev.gestionscolaire.database.AppPaths;
import com.fasodev.gestionscolaire.database.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.io.Console;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

/**
 * Outil de dépannage : remplace le mot de passe d'un compte oublié.
 *
 * Fermez Novera, puis lancez depuis le dossier du projet :
 *
 *   mvn compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.mainClass=com.fasodev.gestionscolaire.tools.ResetMotDePasse"
 *
 * La base est d'abord sauvegardée à côté de l'originale. Aucune donnée n'est supprimée.
 */
public final class ResetMotDePasse {

    private static final int LONGUEUR_MIN = 6;

    private ResetMotDePasse() {}

    public static void main(String[] args) throws Exception {
        File base = new File(AppPaths.getDatabaseFilePath());
        if (!base.exists()) {
            System.out.println("Base introuvable : " + base.getAbsolutePath());
            return;
        }

        Path sauvegarde = Path.of(base.getAbsolutePath()
            + ".sauvegarde-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        Files.copy(base.toPath(), sauvegarde);
        System.out.println("Sauvegarde créée : " + sauvegarde + "\n");

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

            List<Integer> ids = new ArrayList<>();
            System.out.println("Comptes existants :");
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT id, nom_utilisateur, role, actif FROM utilisateurs ORDER BY id")) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    System.out.println("  [" + rs.getInt("id") + "] " + rs.getString("nom_utilisateur")
                        + "  (" + ("ADMIN".equals(rs.getString("role")) ? "Directeur" : "Secrétaire")
                        + (rs.getInt("actif") == 1 ? "" : ", désactivé") + ")");
                }
            }
            if (ids.isEmpty()) {
                System.out.println("Aucun compte dans la base.");
                return;
            }

            Scanner entree = new Scanner(System.in);
            System.out.print("\nNuméro du compte à réinitialiser : ");
            String choix = entree.nextLine().trim();
            int id;
            try {
                id = Integer.parseInt(choix);
            } catch (NumberFormatException e) {
                id = -1;
            }
            if (!ids.contains(id)) {
                System.out.println("Numéro invalide. Rien n'a été modifié.");
                return;
            }

            String motDePasse = lire("Nouveau mot de passe (" + LONGUEUR_MIN + " caractères minimum) : ", entree);
            if (motDePasse.length() < LONGUEUR_MIN) {
                System.out.println("Trop court. Rien n'a été modifié.");
                return;
            }
            String confirmation = lire("Confirmez le mot de passe : ", entree);
            if (!motDePasse.equals(confirmation)) {
                System.out.println("Les deux saisies sont différentes. Rien n'a été modifié.");
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE utilisateurs SET password_hash = ?, actif = 1 WHERE id = ?")) {
                ps.setString(1, BCrypt.hashpw(motDePasse, BCrypt.gensalt()));
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            System.out.println("\nTerminé. Vous pouvez vous connecter avec le nouveau mot de passe.");
        }
    }

    /** Saisie masquée quand c'est possible ; sinon (Maven, IDE) la saisie reste visible. */
    private static String lire(String question, Scanner entree) {
        Console console = System.console();
        if (console != null) {
            char[] saisie = console.readPassword(question);
            return saisie == null ? "" : new String(saisie);
        }
        System.out.print(question);
        return entree.nextLine();
    }
}
