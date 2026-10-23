package edu.college.jobportal.gui;

import edu.college.jobportal.dao.ApplicantDAO;
import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.model.ApplicantJob;
import edu.college.jobportal.model.EmployerApplicant;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
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
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Employer view of applications for jobs owned by the authenticated employer. */
public class EmployerApplicantsFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color MUTED = new Color(100, 116, 139);

    private record JobFilter(Integer jobId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record StatusFilter(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final AuthenticatedUser employer;
    private final Runnable returnToDashboard;
    private final ApplicantDAO applicantDAO = new ApplicantDAO();
    private final JComboBox<JobFilter> jobFilter = new JComboBox<>();
    private final JComboBox<StatusFilter> statusFilter = new JComboBox<>();
    private final JComboBox<String> updateStatus = new JComboBox<>();
    private final JLabel statusLabel = new JLabel("Loading jobs and applications…", SwingConstants.LEFT);
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton detailsButton = new JButton("Applicant Details");
    private final JButton updateButton = new JButton("Update Status");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Applicant", "Email", "Phone", "Job Title", "Application Date", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable applicantsTable = new JTable(tableModel);
    private List<EmployerApplicant> applicants = List.of();
    private boolean busy;

    public EmployerApplicantsFrame(AuthenticatedUser employer, Runnable returnToDashboard) {
        super("View Applicants");
        this.employer = employer;
        this.returnToDashboard = returnToDashboard;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(940, 570));
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                EmployerApplicantsFrame.this.returnToDashboard.run();
            }
        });
        loadJobsAndStatuses();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(new Color(242, 246, 251));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));

        JLabel heading = new JLabel("Applicants", SwingConstants.LEFT);
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(NAVY);
        JLabel subtitle = new JLabel("Review your applicants; status choices use values already stored in the database");
        subtitle.setForeground(MUTED);
        JPanel title = new JPanel(new GridLayout(2, 1, 0, 4));
        title.setOpaque(false);
        title.add(heading);
        title.add(subtitle);

        JPanel filters = new JPanel(new GridLayout(2, 2, 12, 5));
        filters.setOpaque(false);
        filters.add(label("Job"));
        filters.add(label("Application status"));
        jobFilter.addItem(new JobFilter(null, "All my jobs"));
        statusFilter.addItem(new StatusFilter(null, "All statuses"));
        filters.add(jobFilter);
        filters.add(statusFilter);
        refreshButton.addActionListener(event -> loadApplicants());
        JPanel north = new JPanel(new BorderLayout(0, 12));
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(filters, BorderLayout.CENTER);

        applicantsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        applicantsTable.setAutoCreateRowSorter(true);
        applicantsTable.setFillsViewportHeight(true);
        applicantsTable.setRowHeight(25);
        applicantsTable.getSelectionModel().addListSelectionListener(this::selectionChanged);
        JScrollPane tableScroll = new JScrollPane(applicantsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(215, 224, 236)));

        detailsButton.setEnabled(false);
        updateButton.setEnabled(false);
        detailsButton.addActionListener(event -> showApplicantDetails());
        updateButton.addActionListener(event -> saveStatus());
        JPanel statusActions = new JPanel(new GridLayout(1, 2, 8, 0));
        statusActions.setOpaque(false);
        statusActions.add(updateStatus);
        statusActions.add(updateButton);
        JPanel rowActions = new JPanel(new GridLayout(1, 2, 8, 0));
        rowActions.setOpaque(false);
        rowActions.add(detailsButton);
        rowActions.add(statusActions);
        JButton backButton = new JButton("Back to Dashboard");
        backButton.addActionListener(event -> dispose());
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.add(backButton, BorderLayout.WEST);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(refreshButton, BorderLayout.EAST);
        JPanel actions = new JPanel(new BorderLayout(0, 8));
        actions.setOpaque(false);
        actions.add(rowActions, BorderLayout.NORTH);
        actions.add(footer, BorderLayout.SOUTH);

        jobFilter.addActionListener(event -> loadApplicants());
        statusFilter.addActionListener(event -> loadApplicants());
        updateStatus.addActionListener(event -> updateButtonState());
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

    private void loadJobsAndStatuses() {
        setBusy(true);
        new SwingWorker<FilterData, Void>() {
            @Override
            protected FilterData doInBackground() throws SQLException {
                return new FilterData(applicantDAO.listJobsForEmployer(employer.userId()),
                        applicantDAO.listExistingStatuses());
            }

            @Override
            protected void done() {
                try {
                    FilterData data = get();
                    for (ApplicantJob job : data.jobs()) {
                        jobFilter.addItem(new JobFilter(job.jobId(), job.toString()));
                    }
                    for (String status : data.statuses()) {
                        statusFilter.addItem(new StatusFilter(status, status));
                        updateStatus.addItem(status);
                    }
                    setBusy(false);
                    loadApplicants();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setBusy(false);
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    setBusy(false);
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void loadApplicants() {
        if (busy || jobFilter.getSelectedItem() == null) {
            return;
        }
        setBusy(true);
        statusLabel.setText("Loading applicants…");
        JobFilter selectedJob = (JobFilter) jobFilter.getSelectedItem();
        StatusFilter selectedStatus = (StatusFilter) statusFilter.getSelectedItem();
        Integer jobId = selectedJob.jobId();
        String status = selectedStatus == null ? null : selectedStatus.value();
        new SwingWorker<List<EmployerApplicant>, Void>() {
            @Override
            protected List<EmployerApplicant> doInBackground() throws SQLException {
                return applicantDAO.listApplicants(employer.userId(), jobId, status);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    applicants = get();
                    tableModel.setRowCount(0);
                    for (EmployerApplicant applicant : applicants) {
                        tableModel.addRow(new Object[]{applicant.name(), applicant.email(), applicant.phone(),
                                applicant.jobTitle(), applicant.applicationDate() == null ? ""
                                : DateFormat.getDateTimeInstance().format(applicant.applicationDate()),
                                applicant.status()});
                    }
                    if (applicants.isEmpty()) {
                        statusLabel.setText(jobFilter.getItemCount() == 1
                                ? "You have not posted any jobs yet."
                                : "No applicants match the selected job and status filters.");
                    } else {
                        statusLabel.setText(applicants.size() + " applicant(s)");
                    }
                    updateButtonState();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void showApplicantDetails() {
        EmployerApplicant applicant = selectedApplicant();
        if (applicant == null) {
            return;
        }
        String details = "Applicant: " + value(applicant.name())
                + "\nEmail: " + value(applicant.email())
                + "\nPhone: " + value(applicant.phone())
                + "\nJob: " + value(applicant.jobTitle())
                + "\nApplication status: " + value(applicant.status())
                + "\n\nEducation\n" + value(applicant.education())
                + "\n\nExperience\n" + value(applicant.experience())
                + "\n\nSkills\n" + value(applicant.skills())
                + "\n\nLocation\n" + value(applicant.location());
        JTextArea text = new JTextArea(details, 20, 58);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font("SansSerif", Font.PLAIN, 13));
        text.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(text);
        scrollPane.setPreferredSize(new Dimension(580, 390));
        JOptionPane.showMessageDialog(this, scrollPane, "Applicant Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveStatus() {
        EmployerApplicant applicant = selectedApplicant();
        String chosenStatus = (String) updateStatus.getSelectedItem();
        if (applicant == null || chosenStatus == null || busy) {
            return;
        }
        int confirmation = JOptionPane.showConfirmDialog(this,
                "Update this application's status to “" + chosenStatus + "”?",
                "Confirm status change", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true);
        statusLabel.setText("Saving application status…");
        new SwingWorker<ApplicantDAO.ApplicantStatusUpdate, Void>() {
            @Override
            protected ApplicantDAO.ApplicantStatusUpdate doInBackground() throws SQLException {
                return applicantDAO.updateStatus(employer.userId(), applicant.applicationId(),
                        applicant.jobId(), chosenStatus);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    ApplicantDAO.ApplicantStatusUpdate result = get();
                    if (!result.statusIsAvailable()) {
                        JOptionPane.showMessageDialog(EmployerApplicantsFrame.this,
                                "Choose a status already present in the applications table.",
                                "Status unavailable", JOptionPane.WARNING_MESSAGE);
                        loadApplicants();
                    } else if (!result.updated()) {
                        JOptionPane.showMessageDialog(EmployerApplicantsFrame.this,
                                "This application could not be found for one of your jobs.",
                                "Application unavailable", JOptionPane.WARNING_MESSAGE);
                        loadApplicants();
                    } else {
                        JOptionPane.showMessageDialog(EmployerApplicantsFrame.this,
                                "Application status updated.", "Status updated", JOptionPane.INFORMATION_MESSAGE);
                        statusFilter.setSelectedIndex(0);
                        loadApplicants();
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

    private EmployerApplicant selectedApplicant() {
        int viewRow = applicantsTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return applicants.get(applicantsTable.convertRowIndexToModel(viewRow));
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            updateButtonState();
        }
    }

    private void updateButtonState() {
        EmployerApplicant applicant = selectedApplicant();
        boolean selected = applicant != null && !busy;
        detailsButton.setEnabled(selected);
        updateButton.setEnabled(selected && updateStatus.getSelectedItem() != null);
    }

    private void setBusy(boolean value) {
        busy = value;
        jobFilter.setEnabled(!value);
        statusFilter.setEnabled(!value);
        refreshButton.setEnabled(!value);
        updateStatus.setEnabled(!value && updateStatus.getItemCount() > 0);
        updateButtonState();
    }

    private void showDatabaseError(Throwable cause) {
        if (cause != null) {
            System.err.println("Employer applicant operation failed: " + cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                System.err.printf("SQLState=%s, vendorCode=%d%n", sqlException.getSQLState(), sqlException.getErrorCode());
            }
        }
        statusLabel.setText("Could not load or update applicant data.");
        JOptionPane.showMessageDialog(this,
                "Applicant data could not be processed. Check the database connection and try again.",
                "Database error", JOptionPane.ERROR_MESSAGE);
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }

    private record FilterData(List<ApplicantJob> jobs, List<String> statuses) {
    }
}
