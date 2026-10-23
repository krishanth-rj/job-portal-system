package edu.college.jobportal.gui;

import edu.college.jobportal.dao.ApplicationDAO;
import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.dao.JobDAO;
import edu.college.jobportal.model.JobListing;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Active-vacancy search and application screen for the authenticated seeker. */
public class JobSearchFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color MUTED = new Color(100, 116, 139);

    private final AuthenticatedUser user;
    private final Runnable returnToDashboard;
    private final JobDAO jobDAO = new JobDAO();
    private final ApplicationDAO applicationDAO = new ApplicationDAO();
    private final JTextField keywordField = new JTextField(22);
    private final JTextField locationField = new JTextField(15);
    private final JLabel statusLabel = new JLabel("Loading active vacancies…", SwingConstants.LEFT);
    private final JButton searchButton = new JButton("Search");
    private final JButton clearButton = new JButton("Clear Filters");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton detailsButton = new JButton("View Details");
    private final JButton applyButton = new JButton("Apply");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Job Title", "Company", "Location", "Job Type", "Salary", "Experience", "Required Skills", "Posted"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable jobsTable = new JTable(tableModel);
    private List<JobListing> currentJobs = List.of();
    private boolean busy;

    public JobSearchFrame(AuthenticatedUser user, Runnable returnToDashboard) {
        super("Search Jobs");
        this.user = user;
        this.returnToDashboard = returnToDashboard;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(930, 570));
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                JobSearchFrame.this.returnToDashboard.run();
            }
        });
        loadJobs();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(new Color(242, 246, 251));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));

        JLabel heading = new JLabel("Find your next opportunity", SwingConstants.LEFT);
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(NAVY);
        JLabel subtitle = new JLabel("Search active vacancies by role, company, or location");
        subtitle.setForeground(MUTED);
        JPanel title = new JPanel(new GridLayout(2, 1, 0, 4));
        title.setOpaque(false);
        title.add(heading);
        title.add(subtitle);

        JPanel filters = new JPanel(new BorderLayout(10, 0));
        filters.setOpaque(false);
        JPanel filterFields = new JPanel(new GridLayout(2, 2, 10, 5));
        filterFields.setOpaque(false);
        filterFields.add(label("Keyword"));
        filterFields.add(label("Location"));
        keywordField.setToolTipText("Job title, company, skills, or details");
        locationField.setToolTipText("Job or company location");
        filterFields.add(keywordField);
        filterFields.add(locationField);
        JPanel filterButtons = new JPanel(new GridLayout(0, 1, 0, 6));
        filterButtons.setOpaque(false);
        searchButton.addActionListener(event -> loadJobs());
        clearButton.addActionListener(event -> {
            keywordField.setText("");
            locationField.setText("");
            loadJobs();
        });
        refreshButton.addActionListener(event -> loadJobs());
        filterButtons.add(searchButton);
        filterButtons.add(clearButton);
        filterButtons.add(refreshButton);
        filters.add(filterFields, BorderLayout.CENTER);
        filters.add(filterButtons, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(filters, BorderLayout.CENTER);

        jobsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jobsTable.setAutoCreateRowSorter(true);
        jobsTable.setFillsViewportHeight(true);
        jobsTable.setRowHeight(25);
        jobsTable.getSelectionModel().addListSelectionListener(this::selectionChanged);
        JScrollPane tableScroll = new JScrollPane(jobsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(215, 224, 236)));

        detailsButton.setEnabled(false);
        applyButton.setEnabled(false);
        detailsButton.addActionListener(event -> showSelectedDetails());
        applyButton.addActionListener(event -> applyToSelectedJob());
        JButton backButton = new JButton("Back to Dashboard");
        backButton.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new BorderLayout(10, 0));
        actions.setOpaque(false);
        JPanel rightActions = new JPanel(new GridLayout(1, 2, 8, 0));
        rightActions.setOpaque(false);
        rightActions.add(detailsButton);
        rightActions.add(applyButton);
        actions.add(backButton, BorderLayout.WEST);
        actions.add(statusLabel, BorderLayout.CENTER);
        actions.add(rightActions, BorderLayout.EAST);

        root.add(north, BorderLayout.NORTH);
        root.add(tableScroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(NAVY);
        return label;
    }

    private void loadJobs() {
        if (busy) {
            return;
        }
        setBusy(true);
        statusLabel.setText("Loading vacancies…");
        String keyword = keywordField.getText().trim();
        String location = locationField.getText().trim();
        new SwingWorker<List<JobListing>, Void>() {
            @Override
            protected List<JobListing> doInBackground() throws SQLException {
                return jobDAO.searchActiveJobs(keyword, location);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    currentJobs = get();
                    tableModel.setRowCount(0);
                    for (JobListing job : currentJobs) {
                        tableModel.addRow(new Object[]{job.jobTitle(), job.companyName(),
                                firstNonBlank(job.location(), job.companyLocation()), job.jobType(),
                                formatSalary(job.salary()), job.experienceRequired(), job.requiredSkills(),
                                job.postedDate() == null ? "" : DateFormat.getDateInstance().format(job.postedDate())});
                    }
                    statusLabel.setText(currentJobs.isEmpty()
                            ? "No active vacancies match these filters." : currentJobs.size() + " active vacancy/vacancies");
                    updateSelectionActions();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void applyToSelectedJob() {
        JobListing selected = selectedJob();
        if (selected == null || busy) {
            return;
        }
        setBusy(true);
        statusLabel.setText("Submitting application…");
        new SwingWorker<ApplicationDAO.ApplyResult, Void>() {
            @Override
            protected ApplicationDAO.ApplyResult doInBackground() throws SQLException {
                return applicationDAO.applyToJob(user.userId(), selected.jobId());
            }

            @Override
            protected void done() {
                setBusy(false);
                statusLabel.setText(" ");
                try {
                    switch (get()) {
                        case APPLIED -> JOptionPane.showMessageDialog(JobSearchFrame.this,
                                "Your application was submitted.", "Application submitted", JOptionPane.INFORMATION_MESSAGE);
                        case ALREADY_APPLIED -> JOptionPane.showMessageDialog(JobSearchFrame.this,
                                "You have already applied for this job.", "Already applied", JOptionPane.INFORMATION_MESSAGE);
                        case JOB_UNAVAILABLE -> JOptionPane.showMessageDialog(JobSearchFrame.this,
                                "This vacancy is no longer active. The list will be refreshed.",
                                "Vacancy unavailable", JOptionPane.INFORMATION_MESSAGE);
                        case NO_SEEKER_PROFILE -> JOptionPane.showMessageDialog(JobSearchFrame.this,
                                "This account does not have a job seeker profile yet, so an application cannot be submitted. "
                                        + "Profile setup is not available in this version.",
                                "Profile required", JOptionPane.WARNING_MESSAGE);
                    }
                    loadJobs();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void showSelectedDetails() {
        JobListing job = selectedJob();
        if (job == null) {
            return;
        }
        String companyAddress = firstNonBlank(job.companyLocation(), "Not specified");
        String details = "Job title: " + value(job.jobTitle())
                + "\nCompany: " + value(job.companyName())
                + "\nCompany location: " + companyAddress
                + "\nJob location: " + firstNonBlank(job.location(), "Not specified")
                + "\nJob type: " + value(job.jobType())
                + "\nSalary: " + formatSalary(job.salary())
                + "\nExperience required: " + value(job.experienceRequired())
                + "\nRequired skills: " + value(job.requiredSkills())
                + "\nCompany website: " + value(job.companyWebsite())
                + "\n\nCompany information\n" + value(job.companyDescription())
                + "\n\nJob description\n" + value(job.description());
        JTextArea text = new JTextArea(details, 22, 62);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font("SansSerif", Font.PLAIN, 13));
        text.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(text);
        scrollPane.setPreferredSize(new Dimension(620, 440));
        JOptionPane.showMessageDialog(this, scrollPane, "Job Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private JobListing selectedJob() {
        int viewRow = jobsTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = jobsTable.convertRowIndexToModel(viewRow);
        return currentJobs.get(modelRow);
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            updateSelectionActions();
        }
    }

    private void updateSelectionActions() {
        boolean selected = !busy && jobsTable.getSelectedRow() >= 0;
        detailsButton.setEnabled(selected);
        applyButton.setEnabled(selected);
    }

    private void setBusy(boolean value) {
        busy = value;
        searchButton.setEnabled(!value);
        clearButton.setEnabled(!value);
        refreshButton.setEnabled(!value);
        keywordField.setEnabled(!value);
        locationField.setEnabled(!value);
        updateSelectionActions();
    }

    private void showDatabaseError(Throwable cause) {
        if (cause != null) {
            System.err.println("Job seeker database operation failed: " + cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                System.err.printf("SQLState=%s, vendorCode=%d%n", sqlException.getSQLState(), sqlException.getErrorCode());
            }
        }
        statusLabel.setText("Could not load job portal data.");
        JOptionPane.showMessageDialog(this,
                "The database request could not be completed. Check the database connection and try again.",
                "Database error", JOptionPane.ERROR_MESSAGE);
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "Not specified" : value;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? value(fallback) : primary;
    }

    private static String formatSalary(BigDecimal salary) {
        return salary == null ? "Not specified" : salary.toPlainString();
    }
}
