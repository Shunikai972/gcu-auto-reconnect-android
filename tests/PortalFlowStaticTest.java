package fr.gcu.jardsurmer.autoconnect;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class PortalFlowStaticTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("HTML path required");
        String html = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        String pageUrl = "https://jard-sur-mer.gcuf.fr/intercept.php?res=notyet&challenge=TEST_CHALLENGE&userurl=http%3A%2F%2Fconnectivitycheck.gstatic.com%2Fgenerate_204";
        HtmlFormParser.Form form = HtmlFormParser.parseBest(pageUrl, html);
        require("POST".equals(form.method), "method");
        require("https://jard-sur-mer.gcuf.fr/intercept.php".equals(form.action), "action=" + form.action);
        require("username".equals(form.usernameField), "username field=" + form.usernameField);
        require("password".equals(form.passwordField), "password field=" + form.passwordField);
        String body = new String(form.buildBody("TEST_USER", "TEST_PASS"), StandardCharsets.UTF_8);
        require(body.contains("username=TEST_USER"), "username missing");
        require(body.contains("password=TEST_PASS"), "password missing");
        require(body.contains("button=Authentification"), "submit missing: " + body);
        require(body.contains("challenge="), "challenge missing");
        require(body.contains("userurl=http%3A%2F%2Fconnectivitycheck.gstatic.com%2Fgenerate_204"), "userurl missing");
        System.out.println("PORTAL_FORM_TEST=PASS");
        System.out.println("FORM_ACTION=" + form.action);
        System.out.println("BODY_FIELDS=challenge,userurl,username,password,button");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
