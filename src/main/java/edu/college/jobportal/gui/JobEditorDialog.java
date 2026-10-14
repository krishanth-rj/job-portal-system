package edu.college.jobportal.gui;

import edu.college.jobportal.model.JobEntry;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Modal form for creating or editing one job. */
final class JobEditorDialog extends JDialog {
    private final JobEntry existing;
    private final JTextField titleField = new JTextField(28);
    private final JTextArea descriptionArea = new JTextArea(4, 28);
    private final JTextArea skillsArea = new JTextArea(3, 28);
    private final JTextField locationField = new JTextField(28);
    private final JTextField salaryField = new JTextField(28);
    private final JTextField experienceField = new JTextField(28);
    private final JComboBox<String> typeCombo = new JComboBox<>(
            new String[]{"Full-time", "Part-time", "Internship", "Contract", "Temporary"});
    private JobEntry result;

    private JobEditorDialog(Frame owner, JobEntry existing) {
        super(owner, existing == null ? "Add Job" : "Edit Job", true);
        this.existing = existing;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        populateExisting();
        pack();
        setMinimumSize(new Dimension(540, 650));
        setLocationRelativeTo(owner);
    }

    static JobEntry showDialog(Frame owner, JobEntry existing) {
        JobEditorDialog dialog = new JobEditorDialog(owner, existing);
        dialog.setVisible(true);
        return dialog.result;
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel fields = new JPanel(new GridBagLayout());
        int row = 0;
        addField(fields, "Job title *", titleField, row++);
        addField(fields, "Description *", new JScrollPane(descriptionArea), row++);
        addField(fields, "Required skills", new JScrollPane(skillsArea), row++);
        addField(fields, "Location", locationField, row++);
        addField(fields, "Salary (optional)", salaryField, row++);
        addField(fields, "Experience required", experienceField, row++);
        addField(fields, "Job type", typeCombo, row);

        JButton save = new JButton("Save");
        save.addActionListener(event -> saveJob());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JPanel actions = new JPanel();
        actions.add(save);
        actions.add(cancel);
        root.add(fields, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        return root;
    }

    private void addField(JPanel panel, String labelText, java.awt.Component field, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row * 2;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(row == 0 ? 0 : 9, 0, 4, 0);
        panel.add(new JLabel(labelText), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        field.setPreferredSize(field instanceof JScrollPane ? new Dimension(350, 80) : new Dimension(350, 36));
        panel.add(field, constraints);
    }

    private void populateExisting() {
        if (existing == null) {
            return;
        }
        titleField.setText(existing.jobTitle());
        descriptionArea.setText(existing.description());
        skillsArea.setText(existing.requiredSkills());
        locationField.setText(existing.location());
        salaryField.setText(existing.salary() == null ? "" : existing.salary().toPlainString());
        experienceField.setText(existing.experienceRequired());
        if (existing.jobType() != null) {
            typeCombo.setSelectedItem(existing.jobType());
            if (typeCombo.getSelectedIndex() < 0) {
                typeCombo.addItem(existing.jobType());
                typeCombo.setSelectedItem(existing.jobType());
            }
        }
    }

    private void saveJob() {
        String title = titleField.getText().trim();
        String description = descriptionArea.getText().trim();
        String skills = skillsArea.getText().trim();
        String location = locationField.getText().trim();
        String experience = experienceField.getText().trim();
        String type = (String) typeCombo.getSelectedItem();
        if (title.isEmpty() || description.isEmpty()) {
            showValidationError("Job title and description are required.");
            return;
        }
        if (title.length() > 150 || location.length() > 100 || experience.length() > 100
                || (type != null && type.length() > 50)) {
            showValidationError("One or more fields exceed the database column length.");
            return;
        }

        BigDecimal salary = null;
        String rawSalary = salaryField.getText().trim();
        if (!rawSalary.isEmpty()) {
            try {
                salary = new BigDecimal(rawSalary).setScale(2, RoundingMode.UNNECESSARY);
            } catch (NumberFormatException | ArithmeticException exception) {
                showValidationError("Salary must be a non-negative number with at most two decimal places.");
                return;
            }
            if (salary.signum() < 0 || salary.precision() - salary.scale() > 8) {
                showValidationError("Salary must fit the database DECIMAL(10,2) range.");
                return;
            }
        }

        result = new JobEntry(existing == null ? 0 : existing.jobId(), title, description,
                emptyToNull(skills), emptyToNull(location), salary, emptyToNull(experience), type,
                existing == null ? null : existing.postedDate(),
                existing == null ? "ACTIVE" : existing.status());
        dispose();
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(this, message, "Check job details", JOptionPane.WARNING_MESSAGE);
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
