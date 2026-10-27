package edu.college.jobportal.gui;

import edu.college.jobportal.dao.ApplicationDAO;
import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.dao.UserSession;
import edu.college.jobportal.model.ApplicationEntry;
import edu.college.jobportal.repository.ApplicationRepository;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Application history for the authenticated job seeker only. */
public class MyApplicationsFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private final AuthenticatedUser user;
    private final Runnable returnToDashboard;
    private final ApplicationRepository applicationDAO = new ApplicationDAO();
    private final JLabel statusLabel = new JLabel("Loading your applications…", SwingConstants.LEFT);
    private final JButton refreshButton = new JButton("Refresh");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Job Title", "Company", "Application Date", "Application Status", "Location", "Vacancy Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public MyApplicationsFrame(AuthenticatedUser user, Runnable returnToDashboard) {
        super("My Applications");
        this.user = user;
        if (!UserSession.isCurrent(user)) {
            throw new IllegalStateException("An active job seeker session is required.");
        }
        this.returnToDashboard = returnToDashboard;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(820, 480));
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                MyApplicationsFrame.this.returnToDashboard.run();
            }
        });
        refreshApplications();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(new Color(242, 246, 251));
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));

        JLabel heading = new JLabel("My Applications", SwingConstants.LEFT);
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(NAVY);
        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(215, 224, 236)));

        JButton backButton = new JButton("Back to Dashboard");
        backButton.addActionListener(event -> dispose());
        refreshButton.addActionListener(event -> refreshApplications());
        JPanel actions = new JPanel(new BorderLayout(10, 0));
        actions.setOpaque(false);
        JPanel right = new JPanel(new BorderLayout(10, 0));
        right.setOpaque(false);
        right.add(statusLabel, BorderLayout.CENTER);
        right.add(refreshButton, BorderLayout.EAST);
        actions.add(backButton, BorderLayout.WEST);
        actions.add(right, BorderLayout.EAST);

        root.add(heading, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private void refreshApplications() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Loading your applications…");
        new SwingWorker<ApplicationRepository.ApplicationsOverview, Void>() {
            @Override
            protected ApplicationRepository.ApplicationsOverview doInBackground() throws SQLException {
                return applicationDAO.listForUser(user.userId());
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                try {
                    ApplicationRepository.ApplicationsOverview overview = get();
                    tableModel.setRowCount(0);
                    List<ApplicationEntry> applications = overview.applications();
                    for (ApplicationEntry application : applications) {
                        tableModel.addRow(new Object[]{application.jobTitle(), application.companyName(),
                                application.applicationDate() == null ? ""
                                        : DateFormat.getDateTimeInstance().format(application.applicationDate()),
                                application.status(), application.jobLocation(), application.jobStatus()});
                    }
                    if (!overview.seekerProfileExists()) {
                        statusLabel.setText("No job seeker profile is linked to this account.");
                    } else if (applications.isEmpty()) {
                        statusLabel.setText("You have not applied to any jobs yet.");
                    } else {
                        statusLabel.setText(applications.size() + " application(s)");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void showDatabaseError(Throwable cause) {
        if (cause != null) {
            System.err.println("Application history query failed: " + cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                System.err.printf("SQLState=%s, vendorCode=%d%n", sqlException.getSQLState(), sqlException.getErrorCode());
            }
        }
        statusLabel.setText("Could not load your applications.");
        JOptionPane.showMessageDialog(this,
                "Your applications could not be loaded. Check the database connection and try again.",
                "Database error", JOptionPane.ERROR_MESSAGE);
    }
}
