package edu.college.jobportal.gui;

import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.dao.UserRole;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/** Dashboard content and available actions are selected from the authenticated database role. */
public class DashboardFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color ACCENT = new Color(39, 104, 196);

    public DashboardFrame(AuthenticatedUser user) {
        super(titleFor(user.role()));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(560, 390));
        setContentPane(buildContent(user));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildContent(AuthenticatedUser user) {
        JPanel root = new JPanel(new BorderLayout(0, 24));
        root.setBackground(new Color(242, 246, 251));
        root.setBorder(BorderFactory.createEmptyBorder(28, 34, 28, 34));

        JLabel heading = new JLabel("Welcome, " + user.name(), SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 24));
        heading.setForeground(NAVY);
        JLabel role = new JLabel(labelFor(user.role()), SwingConstants.CENTER);
        role.setFont(new Font("SansSerif", Font.PLAIN, 15));
        role.setForeground(new Color(100, 116, 139));
        JPanel title = new JPanel(new GridLayout(2, 1, 0, 7));
        title.setOpaque(false);
        title.add(heading);
        title.add(role);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 12));
        actions.setOpaque(false);
        for (String feature : featuresFor(user.role())) {
            JButton button = new JButton(feature);
            button.setFont(new Font("SansSerif", Font.BOLD, 14));
            button.setForeground(NAVY);
            button.setBackground(Color.WHITE);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(215, 224, 236)),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)));
            button.addActionListener(event -> {
                if (user.role() == UserRole.EMPLOYER && "Manage Jobs".equals(feature)) {
                    setVisible(false);
                    new EmployerJobManagementFrame(user, () -> setVisible(true)).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(this,
                            feature + " is coming soon.", "Coming soon", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            actions.add(button);
        }

        JButton logoutButton = new JButton("Log out");
        logoutButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        logoutButton.setForeground(Color.WHITE);
        logoutButton.setBackground(ACCENT);
        logoutButton.setFocusPainted(false);
        logoutButton.setBorder(BorderFactory.createEmptyBorder(11, 16, 11, 16));
        logoutButton.addActionListener(event -> {
            dispose();
            new LoginFrame().setVisible(true);
        });
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(logoutButton, BorderLayout.EAST);

        root.add(title, BorderLayout.NORTH);
        root.add(actions, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private static String titleFor(UserRole role) {
        return switch (role) {
            case JOB_SEEKER -> "Job Seeker Dashboard";
            case EMPLOYER -> "Employer Dashboard";
            case ADMIN -> "Admin Dashboard";
        };
    }

    private static String labelFor(UserRole role) {
        return switch (role) {
            case JOB_SEEKER -> "Job Seeker";
            case EMPLOYER -> "Employer";
            case ADMIN -> "Administrator";
        };
    }

    private static List<String> featuresFor(UserRole role) {
        return switch (role) {
            case JOB_SEEKER -> List.of("Search Jobs", "My Applications", "My Profile");
            case EMPLOYER -> List.of("Post a Job", "Manage Jobs", "View Applicants");
            case ADMIN -> List.of("Manage Users", "Manage Jobs");
        };
    }
}
