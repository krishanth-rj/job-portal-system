package edu.college.jobportal.gui;

import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.dao.JobSeekerProfileDAO;
import edu.college.jobportal.dao.UserRole;
import edu.college.jobportal.dao.UserSession;
import edu.college.jobportal.model.JobSeekerProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/** Editable personal profile for the currently authenticated job seeker. */
public class JobSeekerProfileFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9() -]+");

    private final AuthenticatedUser user;
    private final Runnable returnToDashboard;
    private final JobSeekerProfileDAO profileDAO = new JobSeekerProfileDAO();
    private final JTextField nameField = new JTextField(28);
    private final JTextField emailField = new JTextField(28);
    private final JTextField phoneField = new JTextField(28);
    private final JTextField educationField = new JTextField(28);
    private final JTextArea skillsArea = new JTextArea(4, 28);
    private final JTextField experienceField = new JTextField(28);
    private final JTextField locationField = new JTextField(28);
    private final JLabel statusLabel = new JLabel("Loading your profile…", SwingConstants.LEFT);
    private final JButton editButton = new JButton("Edit Profile");
    private final JButton saveButton = new JButton("Save Changes");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton refreshButton = new JButton("Refresh");
    private JobSeekerProfile loadedProfile;
    private boolean editing;
    private boolean busy;

    public JobSeekerProfileFrame(AuthenticatedUser user, Runnable returnToDashboard) {
        super("My Profile");
        if (user == null || user.role() != UserRole.JOB_SEEKER || !UserSession.isCurrent(user)) {
            throw new IllegalStateException("An active job seeker session is required.");
        }
        this.user = user;
        this.returnToDashboard = returnToDashboard;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(620, 560));
        setContentPane(buildContent());
        pack();
        setSize(760, 700);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                JobSeekerProfileFrame.this.returnToDashboard.run();
            }
        });
        loadProfile();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(new Color(242, 246, 251));
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));

        JLabel heading = new JLabel("My Profile", SwingConstants.LEFT);
        heading.setFont(new Font("SansSerif", Font.BOLD, 23));
        heading.setForeground(NAVY);
        JLabel subtitle = new JLabel("Review and update your job seeker information.", SwingConstants.LEFT);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        JPanel title = new JPanel(new BorderLayout(0, 5));
        title.setOpaque(false);
        title.add(heading, BorderLayout.NORTH);
        title.add(subtitle, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 224, 236)),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        addField(form, "Full name", nameField, 0);
        addField(form, "Email address (read-only)", emailField, 1);
        emailField.setEditable(false);
        emailField.setBackground(new Color(243, 246, 250));
        addField(form, "Phone number", phoneField, 2);
        addField(form, "Education", educationField, 3);
        addField(form, "Skills", new JScrollPane(skillsArea), 4);
        skillsArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        skillsArea.setLineWrap(true);
        skillsArea.setWrapStyleWord(true);
        addField(form, "Experience", experienceField, 5);
        addField(form, "Location", locationField, 6);
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton backButton = new JButton("Back to Dashboard");
        backButton.addActionListener(event -> dispose());
        editButton.addActionListener(event -> setEditing(true));
        saveButton.addActionListener(event -> saveProfile());
        cancelButton.addActionListener(event -> cancelEditing());
        refreshButton.addActionListener(event -> refreshProfile());
        JPanel editActions = new JPanel(new GridLayout(2, 2, 6, 6));
        editActions.setOpaque(false);
        editActions.add(editButton);
        editActions.add(saveButton);
        editActions.add(cancelButton);
        editActions.add(refreshButton);
        JPanel actionRow = new JPanel(new BorderLayout(10, 0));
        actionRow.setOpaque(false);
        actionRow.add(backButton, BorderLayout.WEST);
        actionRow.add(editActions, BorderLayout.EAST);
        JPanel actions = new JPanel(new BorderLayout(0, 7));
        actions.setOpaque(false);
        actions.add(statusLabel, BorderLayout.NORTH);
        actions.add(actionRow, BorderLayout.SOUTH);

        root.add(title, BorderLayout.NORTH);
        root.add(formScroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        setEditing(false);
        setActionButtonsEnabled(false);
        return root;
    }

    private void addField(JPanel form, String labelText, java.awt.Component field, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row * 2;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(row == 0 ? 0 : 11, 0, 4, 0);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(NAVY);
        form.add(label, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        if (field instanceof JTextField textField) {
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(203, 213, 225)),
                    BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        } else {
            field.setPreferredSize(new Dimension(330, 86));
        }
        form.add(field, constraints);
    }

    private void loadProfile() {
        if (busy || !UserSession.isCurrent(user)) {
            if (!UserSession.isCurrent(user)) {
                dispose();
            }
            return;
        }
        if (editing && loadedProfile != null) {
            populate(loadedProfile);
        }
        setEditing(false);
        busy = true;
        statusLabel.setText("Loading your profile…");
        setActionButtonsEnabled(false);
        new SwingWorker<JobSeekerProfile, Void>() {
            @Override
            protected JobSeekerProfile doInBackground() throws SQLException {
                return profileDAO.loadCurrentProfile();
            }

            @Override
            protected void done() {
                if (!isDisplayable()) {
                    return;
                }
                busy = false;
                try {
                    loadedProfile = get();
                    populate(loadedProfile);
                    setEditing(false);
                    statusLabel.setText(loadedProfile.seekerProfileExists()
                            ? "Profile loaded." : "No seeker profile exists yet. Choose Edit Profile to add your details.");
                    setActionButtonsEnabled(true);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void refreshProfile() {
        if (busy) {
            return;
        }
        if (editing) {
            int answer = JOptionPane.showConfirmDialog(this, "Discard your unsaved changes and refresh?",
                    "Discard changes", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        loadProfile();
    }

    private void saveProfile() {
        if (busy || !UserSession.isCurrent(user)) {
            if (!UserSession.isCurrent(user)) {
                dispose();
            }
            return;
        }
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String education = educationField.getText().trim();
        String skills = skillsArea.getText().trim();
        String experience = experienceField.getText().trim();
        String location = locationField.getText().trim();
        String validationError = validate(name, phone, education, skills, experience, location);
        if (validationError != null) {
            JOptionPane.showMessageDialog(this, validationError, "Check your details", JOptionPane.WARNING_MESSAGE);
            return;
        }

        busy = true;
        statusLabel.setText("Saving your profile…");
        setActionButtonsEnabled(false);
        new SwingWorker<JobSeekerProfile, Void>() {
            @Override
            protected JobSeekerProfile doInBackground() throws SQLException {
                return profileDAO.saveCurrentProfile(name, phone, education, skills, experience, location);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) {
                    return;
                }
                busy = false;
                try {
                    loadedProfile = get();
                    populate(loadedProfile);
                    setEditing(false);
                    statusLabel.setText("Profile saved successfully.");
                    setActionButtonsEnabled(true);
                    JOptionPane.showMessageDialog(JobSeekerProfileFrame.this,
                            "Your profile has been updated.", "Profile saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        JOptionPane.showMessageDialog(JobSeekerProfileFrame.this, cause.getMessage(),
                                "Check your details", JOptionPane.WARNING_MESSAGE);
                        setActionButtonsEnabled(true);
                    } else {
                        showDatabaseError(cause);
                    }
                }
            }
        }.execute();
    }

    private void cancelEditing() {
        if (loadedProfile != null) {
            populate(loadedProfile);
        }
        setEditing(false);
        statusLabel.setText("Changes discarded.");
    }

    private void populate(JobSeekerProfile profile) {
        nameField.setText(valueOrEmpty(profile.name()));
        emailField.setText(valueOrEmpty(profile.email()));
        phoneField.setText(valueOrEmpty(profile.phone()));
        educationField.setText(valueOrEmpty(profile.education()));
        skillsArea.setText(valueOrEmpty(profile.skills()));
        experienceField.setText(valueOrEmpty(profile.experience()));
        locationField.setText(valueOrEmpty(profile.location()));
    }

    private void setEditing(boolean enabled) {
        editing = enabled;
        nameField.setEditable(enabled);
        phoneField.setEditable(enabled);
        educationField.setEditable(enabled);
        skillsArea.setEditable(enabled);
        experienceField.setEditable(enabled);
        locationField.setEditable(enabled);
        emailField.setEditable(false);
        editButton.setEnabled(!enabled && loadedProfile != null && !busy);
        saveButton.setEnabled(enabled && !busy);
        cancelButton.setEnabled(enabled && !busy);
        refreshButton.setEnabled(!busy);
    }

    private void setActionButtonsEnabled(boolean enabled) {
        editButton.setEnabled(enabled && !editing && loadedProfile != null && !busy);
        saveButton.setEnabled(enabled && editing && !busy);
        cancelButton.setEnabled(enabled && editing && !busy);
        refreshButton.setEnabled(!busy);
    }

    private String validate(String name, String phone, String education, String skills,
                            String experience, String location) {
        if (name.isEmpty()) {
            return "Full name is required.";
        }
        if (name.length() > 100 || phone.length() > 15 || education.length() > 150
                || skills.getBytes(StandardCharsets.UTF_8).length > 65_535
                || experience.length() > 100 || location.length() > 100) {
            return "One or more values exceed the allowed database field length.";
        }
        if (!phone.isEmpty()) {
            long digits = phone.chars().filter(Character::isDigit).count();
            if (!PHONE_PATTERN.matcher(phone).matches() || digits < 7 || digits > 15) {
                return "Enter a valid phone number using 7–15 digits.";
            }
        }
        return null;
    }

    private void showDatabaseError(Throwable cause) {
        if (cause != null) {
            System.err.println("Job seeker profile operation failed: " + cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                System.err.printf("SQLState=%s, vendorCode=%d%n", sqlException.getSQLState(),
                        sqlException.getErrorCode());
            }
        }
        statusLabel.setText("Could not load or save your profile.");
        setActionButtonsEnabled(loadedProfile != null);
        JOptionPane.showMessageDialog(this,
                "We could not access your profile. Check the database connection and try again.",
                "Profile unavailable", JOptionPane.ERROR_MESSAGE);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
