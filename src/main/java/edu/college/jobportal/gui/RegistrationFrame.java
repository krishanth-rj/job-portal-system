package edu.college.jobportal.gui;

import edu.college.jobportal.dao.DuplicateEmailException;
import edu.college.jobportal.dao.UserDAO;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/** Registration window; database work runs in a SwingWorker. */
public class RegistrationFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color ACCENT = new Color(39, 104, 196);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9() -]+");

    private final JTextField nameField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JTextField phoneField = new JTextField(24);
    private final JComboBox<AccountRole> roleCombo = new JComboBox<>(AccountRole.values());
    private final JButton registerButton = new JButton("Create account");
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

    public RegistrationFrame() {
        super("Create your Job Portal account");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(540, 650));
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);
        registerButton.addActionListener(event -> submitRegistration());
    }

    private JPanel buildContent() {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(new Color(242, 246, 251));
        JPanel card = new JPanel(new BorderLayout(0, 20));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 232, 241)),
                BorderFactory.createEmptyBorder(30, 38, 26, 38)));

        JLabel heading = new JLabel("Create your account", SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 25));
        heading.setForeground(NAVY);
        JLabel subtitle = new JLabel("Join the Job Portal community", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        JPanel titlePanel = new JPanel(new BorderLayout(0, 7));
        titlePanel.setOpaque(false);
        titlePanel.add(heading, BorderLayout.NORTH);
        titlePanel.add(subtitle, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, "Full name", nameField, 0);
        addField(form, "Email address", emailField, 1);
        addField(form, "Password", passwordField, 2);
        addField(form, "Phone number (optional)", phoneField, 3);
        addField(form, "Register as", roleCombo, 4);

        registerButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        registerButton.setForeground(Color.WHITE);
        registerButton.setBackground(ACCENT);
        registerButton.setFocusPainted(false);
        registerButton.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        registerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> clearFields());
        JButton backButton = new JButton("Back to Login");
        backButton.addActionListener(event -> {
            LoginFrame.showLogin();
            dispose();
        });
        JPanel actions = new JPanel(new GridBagLayout());
        actions.setOpaque(false);
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.insets = new Insets(5, 4, 5, 4);
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        actionConstraints.gridx = 0;
        actionConstraints.weightx = 1;
        actions.add(registerButton, actionConstraints);
        actionConstraints.gridx = 1;
        actionConstraints.weightx = 0;
        actions.add(clearButton, actionConstraints);
        actionConstraints.gridx = 0;
        actionConstraints.gridy = 1;
        actionConstraints.gridwidth = 2;
        actions.add(backButton, actionConstraints);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        bottom.setOpaque(false);
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        card.add(titlePanel, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        GridBagConstraints cardConstraints = new GridBagConstraints();
        cardConstraints.fill = GridBagConstraints.HORIZONTAL;
        cardConstraints.weightx = 1;
        cardConstraints.insets = new Insets(20, 24, 20, 24);
        background.add(card, cardConstraints);
        return background;
    }

    private void addField(JPanel form, String labelText, java.awt.Component field, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row * 2;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(row == 0 ? 0 : 13, 0, 5, 0);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(NAVY);
        form.add(label, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        if (field instanceof JTextField textField) {
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(203, 213, 225)),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        } else if (field instanceof JComboBox<?>) {
            field.setPreferredSize(new Dimension(300, 40));
        }
        form.add(field, constraints);
    }

    private void submitRegistration() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        char[] password = passwordField.getPassword();
        AccountRole role = (AccountRole) roleCombo.getSelectedItem();
        String validationError = validateInput(name, email, password, phone, role);
        if (validationError != null) {
            Arrays.fill(password, '\0');
            JOptionPane.showMessageDialog(this, validationError, "Check your details", JOptionPane.WARNING_MESSAGE);
            return;
        }

        registerButton.setEnabled(false);
        statusLabel.setText("Creating your account…");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws SQLException, DuplicateEmailException {
                return new UserDAO().registerUser(name, email, password, phone, role.databaseValue);
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                passwordField.setText("");
                registerButton.setEnabled(true);
                statusLabel.setText(" ");
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(RegistrationFrame.this,
                                "Your account has been created successfully.", "Registration complete",
                                JOptionPane.INFORMATION_MESSAGE);
                        clearFields();
                    } else {
                        showDatabaseError(null);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof DuplicateEmailException) {
                        JOptionPane.showMessageDialog(RegistrationFrame.this,
                                "An account with that email address already exists.", "Email already registered",
                                JOptionPane.WARNING_MESSAGE);
                        emailField.requestFocusInWindow();
                    } else {
                        showDatabaseError(cause);
                    }
                }
            }
        }.execute();
    }

    private String validateInput(String name, String email, char[] password, String phone, AccountRole role) {
        if (name.isEmpty() || email.isEmpty() || password.length == 0 || role == null) {
            return "Please complete all required fields.";
        }
        if (name.length() > 100) {
            return "Full name must be 100 characters or fewer.";
        }
        if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            return "Enter a valid email address (up to 100 characters).";
        }
        if (password.length < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (passwordUtf8Length(password) > 72) {
            return "Password must be no more than 72 UTF-8 bytes.";
        }
        if (!phone.isEmpty()) {
            long digitCount = phone.chars().filter(Character::isDigit).count();
            if (phone.length() > 15 || !PHONE_PATTERN.matcher(phone).matches()
                    || digitCount < 7 || digitCount > 15) {
                return "Enter a valid phone number using 7–15 digits (spaces, +, parentheses, and hyphens are allowed).";
            }
        }
        return null;
    }

    private int passwordUtf8Length(char[] password) {
        try {
            return StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password)).remaining();
        } catch (CharacterCodingException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private void showDatabaseError(Throwable cause) {
        if (cause != null) {
            System.err.println("Registration database operation failed: " + cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                System.err.printf("SQLState=%s, vendorCode=%d%n", sqlException.getSQLState(), sqlException.getErrorCode());
            }
        }
        JOptionPane.showMessageDialog(this,
                "We could not create your account. Check the database connection and try again.",
                "Registration failed", JOptionPane.ERROR_MESSAGE);
    }

    private void clearFields() {
        nameField.setText("");
        emailField.setText("");
        passwordField.setText("");
        phoneField.setText("");
        roleCombo.setSelectedIndex(0);
        nameField.requestFocusInWindow();
    }

    private enum AccountRole {
        JOB_SEEKER("Job Seeker", "JOB_SEEKER"),
        EMPLOYER("Employer", "EMPLOYER");

        private final String label;
        private final String databaseValue;

        AccountRole(String label, String databaseValue) {
            this.label = label;
            this.databaseValue = databaseValue;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
