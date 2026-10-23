package edu.college.jobportal.gui;

import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.dao.UserSession;
import edu.college.jobportal.dao.UserRole;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Supplier;

/** Dashboard content and available actions are selected from the authenticated database role. */
public class DashboardFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color ACCENT = new Color(39, 104, 196);
    private final AuthenticatedUser user;
    private JFrame activeChild;

    public DashboardFrame(AuthenticatedUser user) {
        super(titleFor(user.role()));
        if (!UserSession.isCurrent(user)) {
            throw new IllegalStateException("A current authenticated session is required to open a dashboard.");
        }
        this.user = user;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(560, 390));
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildContent() {
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
                if (!UserSession.isCurrent(user)) {
                    logout();
                    return;
                }
                if (user.role() == UserRole.JOB_SEEKER && "Search Jobs".equals(feature)) {
                    openChild(() -> new JobSearchFrame(user, this::restoreDashboard));
                } else if (user.role() == UserRole.JOB_SEEKER && "My Applications".equals(feature)) {
                    openChild(() -> new MyApplicationsFrame(user, this::restoreDashboard));
                } else if (user.role() == UserRole.EMPLOYER && "View Applicants".equals(feature)) {
                    openChild(() -> new EmployerApplicantsFrame(user, this::restoreDashboard));
                } else if (user.role() == UserRole.EMPLOYER && "Manage Jobs".equals(feature)) {
                    openChild(() -> new EmployerJobManagementFrame(user, this::restoreDashboard));
                } else {
                    JOptionPane.showMessageDialog(this,
                            feature + " is coming soon.", "Coming soon", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            actions.add(button);
        }

        JButton logoutButton = new JButton("Logout");
        logoutButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        logoutButton.setForeground(Color.WHITE);
        logoutButton.setBackground(ACCENT);
        // Aqua's native painter can ignore custom button colors, making the
        // white Logout label disappear against the dashboard background.
        logoutButton.setUI(new BasicButtonUI());
        logoutButton.setOpaque(true);
        logoutButton.setContentAreaFilled(true);
        logoutButton.setFocusPainted(false);
        logoutButton.setBorder(BorderFactory.createEmptyBorder(11, 16, 11, 16));
        logoutButton.addActionListener(event -> logout());
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(logoutButton, BorderLayout.EAST);

        root.add(title, BorderLayout.NORTH);
        root.add(actions, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private void restoreDashboard() {
        if (!UserSession.isCurrent(user)) {
            dispose();
            return;
        }
        if (activeChild != null && !activeChild.isDisplayable()) {
            activeChild = null;
        }
        setVisible(true);
        toFront();
    }

    private void openChild(Supplier<JFrame> childFactory) {
        if (!UserSession.isCurrent(user)) {
            logout();
            return;
        }
        if (activeChild != null && activeChild.isDisplayable()) {
            activeChild.toFront();
            return;
        }
        activeChild = childFactory.get();
        setVisible(false);
        activeChild.setVisible(true);
    }

    private void logout() {
        boolean endingCurrentSession = UserSession.isCurrent(user);
        UserSession.clear(user);
        JFrame child = activeChild;
        activeChild = null;
        if (child != null && child.isDisplayable()) {
            child.dispose();
        }
        dispose();
        if (endingCurrentSession) {
            LoginFrame.showLogin();
        }
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
