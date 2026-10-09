package edu.college.jobportal.gui;

import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.dao.UserDAO;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Email and password login screen. */
public class LoginFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color ACCENT = new Color(39, 104, 196);
    private static final Color MUTED = new Color(100, 116, 139);

    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JButton loginButton = new JButton("Log in");
    private final JButton registerButton = new JButton("Create an account");
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

    public LoginFrame() {
        super("Job Portal Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(480, 430));
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);
        loginButton.addActionListener(event -> submitLogin());
        registerButton.addActionListener(event -> openRegistration());
        getRootPane().setDefaultButton(loginButton);
    }

    private JPanel buildContent() {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(new Color(242, 246, 251));
        JPanel card = new JPanel(new BorderLayout(0, 20));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 232, 241)),
                BorderFactory.createEmptyBorder(30, 38, 26, 38)));

        JLabel heading = new JLabel("Welcome back", SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 25));
        heading.setForeground(NAVY);
        JLabel subtitle = new JLabel("Sign in to your Job Portal account", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        JPanel title = new JPanel(new BorderLayout(0, 7));
        title.setOpaque(false);
        title.add(heading, BorderLayout.NORTH);
        title.add(subtitle, BorderLayout.SOUTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, "Email address", emailField, 0);
        addField(fields, "Password", passwordField, 1);

        loginButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        loginButton.setForeground(Color.WHITE);
        loginButton.setBackground(ACCENT);
        loginButton.setFocusPainted(false);
        loginButton.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerButton.setFocusPainted(false);

        JPanel actions = new JPanel(new GridBagLayout());
        actions.setOpaque(false);
        GridBagConstraints action = new GridBagConstraints();
        action.gridx = 0;
        action.gridy = 0;
        action.fill = GridBagConstraints.HORIZONTAL;
        action.weightx = 1;
        action.insets = new Insets(4, 0, 5, 0);
        actions.add(loginButton, action);
        action.gridy = 1;
        actions.add(registerButton, action);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        bottom.setOpaque(false);
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        card.add(title, BorderLayout.NORTH);
        card.add(fields, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        GridBagConstraints cardConstraints = new GridBagConstraints();
        cardConstraints.fill = GridBagConstraints.HORIZONTAL;
        cardConstraints.weightx = 1;
        cardConstraints.insets = new Insets(20, 24, 20, 24);
        background.add(card, cardConstraints);
        return background;
    }

    private void addField(JPanel fields, String text, JTextField field, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row * 2;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(row == 0 ? 0 : 15, 0, 5, 0);
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(NAVY);
        fields.add(label, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        fields.add(field, constraints);
    }

    private void submitLogin() {
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        if (email.isEmpty() || password.length == 0) {
            Arrays.fill(password, '\0');
            JOptionPane.showMessageDialog(this, "Enter your email and password.",
                    "Missing details", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setLoginBusy(true);
        statusLabel.setText("Signing in…");
        new SwingWorker<AuthenticatedUser, Void>() {
            @Override
            protected AuthenticatedUser doInBackground() throws SQLException {
                return new UserDAO().authenticate(email, password);
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                passwordField.setText("");
                setLoginBusy(false);
                statusLabel.setText(" ");
                try {
                    AuthenticatedUser user = get();
                    if (user == null) {
                        JOptionPane.showMessageDialog(LoginFrame.this, "Invalid email or password.",
                                "Sign-in failed", JOptionPane.ERROR_MESSAGE);
                        passwordField.requestFocusInWindow();
                        return;
                    }
                    new DashboardFrame(user).setVisible(true);
                    dispose();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void openRegistration() {
        new RegistrationFrame().setVisible(true);
        dispose();
    }

    private void setLoginBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
        emailField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
    }

    private void showDatabaseError(Throwable cause) {
        if (cause != null) {
            System.err.println("Login database operation failed: " + cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                System.err.printf("SQLState=%s, vendorCode=%d%n", sqlException.getSQLState(), sqlException.getErrorCode());
            }
        }
        JOptionPane.showMessageDialog(this,
                "Login is temporarily unavailable. Check the database connection and try again.",
                "Login error", JOptionPane.ERROR_MESSAGE);
    }
}
