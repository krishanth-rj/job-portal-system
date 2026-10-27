package edu.college.jobportal.gui;

import edu.college.jobportal.dao.CompanyDAO;
import edu.college.jobportal.dao.JobDAO;
import edu.college.jobportal.dao.UserRole;
import edu.college.jobportal.dao.AuthenticatedUser;
import edu.college.jobportal.dao.UserSession;
import edu.college.jobportal.model.CompanyProfile;
import edu.college.jobportal.model.JobEntry;
import edu.college.jobportal.repository.JobRepository;

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
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Employer-owned company and job management screen. */
public class EmployerJobManagementFrame extends JFrame {
    private static final Color NAVY = new Color(26, 43, 74);
    private static final Color MUTED = new Color(100, 116, 139);

    private final AuthenticatedUser employer;
    private final Runnable onClose;
    private final CompanyDAO companyDAO = new CompanyDAO();
    private final JobRepository jobDAO = new JobDAO();
    private final boolean openAddJobOnReady;
    private final JobTableModel tableModel = new JobTableModel();
    private final JTable jobsTable = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Loading company profile…", SwingConstants.CENTER);
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton addButton = new JButton("Add Job");
    private final JButton editButton = new JButton("Edit Job");
    private final JButton deactivateButton = new JButton("Deactivate Job");
    private CompanyProfile company;
    private boolean busy;
    private boolean initialAddJobScheduled;

    public EmployerJobManagementFrame(AuthenticatedUser employer, Runnable onClose) {
        this(employer, onClose, false);
    }

    /** Opens this screen and optionally starts the existing Add Job workflow once company setup is ready. */
    public EmployerJobManagementFrame(AuthenticatedUser employer, Runnable onClose, boolean openAddJobOnReady) {
        super("Employer Job Management");
        if (employer.role() != UserRole.EMPLOYER) {
            throw new IllegalArgumentException("Employer access is required.");
        }
        if (!UserSession.isCurrent(employer)) {
            throw new IllegalStateException("An active employer session is required.");
        }
        this.employer = employer;
        this.onClose = onClose;
        this.openAddJobOnReady = openAddJobOnReady;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 560));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                EmployerJobManagementFrame.this.onClose.run();
            }
        });
        setContentPane(loadingPanel());
        pack();
        setLocationRelativeTo(null);
        loadCompany();
    }

    private JPanel loadingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private void loadCompany() {
        statusLabel.setText("Loading company profile…");
        new SwingWorker<CompanyProfile, Void>() {
            @Override
            protected CompanyProfile doInBackground() throws SQLException {
                return companyDAO.findForEmployer(employer.userId());
            }

            @Override
            protected void done() {
                try {
                    company = get();
                    if (company == null) {
                        showCompanySetup();
                    } else {
                        showJobsScreen();
                        if (openAddJobOnReady) {
                            scheduleInitialAddJob();
                        } else {
                            refreshJobs();
                        }
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

    private void showCompanySetup() {
        JLabel heading = new JLabel("Set up your company profile", SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(NAVY);
        JTextField name = new JTextField(28);
        JTextArea description = new JTextArea(4, 28);
        JTextField location = new JTextField(28);
        JTextField website = new JTextField(28);
        JPanel fields = new JPanel(new GridBagLayout());
        addCompanyField(fields, "Company name *", name, 0);
        addCompanyField(fields, "Description", new JScrollPane(description), 1);
        addCompanyField(fields, "Location", location, 2);
        addCompanyField(fields, "Website", website, 3);
        JButton save = new JButton("Save Company Profile");
        JButton cancel = new JButton("Back to Dashboard");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actions.add(save);
        actions.add(cancel);
        JPanel content = new JPanel(new BorderLayout(0, 15));
        content.setBorder(BorderFactory.createEmptyBorder(28, 38, 24, 38));
        content.add(heading, BorderLayout.NORTH);
        content.add(fields, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(600, 500));
        save.addActionListener(event -> {
            String companyName = name.getText().trim();
            String companyDescription = description.getText().trim();
            String companyLocation = location.getText().trim();
            String companyWebsite = website.getText().trim();
            if (companyName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Company name is required.",
                        "Check company details", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (companyName.length() > 150 || companyLocation.length() > 100 || companyWebsite.length() > 200) {
                JOptionPane.showMessageDialog(this, "A company field exceeds its database column length.",
                        "Check company details", JOptionPane.WARNING_MESSAGE);
                return;
            }
            save.setEnabled(false);
            cancel.setEnabled(false);
            new SwingWorker<CompanyProfile, Void>() {
                @Override
                protected CompanyProfile doInBackground() throws SQLException {
                    return companyDAO.createForEmployer(employer.userId(), companyName,
                            companyDescription, companyLocation, companyWebsite);
                }

                @Override
                protected void done() {
                    try {
                        company = get();
                        JOptionPane.showMessageDialog(EmployerJobManagementFrame.this,
                                "Company profile is ready.", "Company saved", JOptionPane.INFORMATION_MESSAGE);
                        showJobsScreen();
                        if (openAddJobOnReady) {
                            scheduleInitialAddJob();
                        } else {
                            refreshJobs();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        save.setEnabled(true);
                        cancel.setEnabled(true);
                        showDatabaseError(exception);
                    } catch (ExecutionException exception) {
                        save.setEnabled(true);
                        cancel.setEnabled(true);
                        showDatabaseError(exception.getCause());
                    }
                }
            }.execute();
        });
        cancel.addActionListener(event -> dispose());
    }

    private void addCompanyField(JPanel panel, String label, java.awt.Component field, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row * 2;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(row == 0 ? 0 : 12, 0, 4, 0);
        panel.add(new JLabel(label), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        field.setPreferredSize(field instanceof JScrollPane ? new Dimension(360, 80) : new Dimension(360, 36));
        panel.add(field, constraints);
    }

    private void showJobsScreen() {
        JLabel heading = new JLabel("My Jobs — " + company.companyName());
        heading.setFont(new Font("SansSerif", Font.BOLD, 20));
        heading.setForeground(NAVY);
        JButton backButton = new JButton("← Back to Dashboard");
        backButton.addActionListener(event -> dispose());
        JPanel headingAndBack = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headingAndBack.setOpaque(false);
        headingAndBack.add(backButton);
        headingAndBack.add(heading);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(headingAndBack, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(addButton);
        actions.add(editButton);
        actions.add(deactivateButton);
        top.add(actions, BorderLayout.EAST);

        jobsTable.setFillsViewportHeight(true);
        jobsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jobsTable.setRowHeight(25);
        jobsTable.getTableHeader().setReorderingAllowed(false);
        jobsTable.getSelectionModel().addListSelectionListener(event -> updateActionState());
        statusLabel.setForeground(MUTED);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(new JScrollPane(jobsTable), BorderLayout.CENTER);
        center.add(statusLabel, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        content.add(top, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(900, 560));

        refreshButton.addActionListener(event -> refreshJobs());
        addButton.addActionListener(event -> addJob());
        editButton.addActionListener(event -> editJob());
        deactivateButton.addActionListener(event -> deactivateJob());
        updateActionState();
    }

    private void refreshJobs() {
        if (company == null || busy) {
            return;
        }
        setBusy(true, "Loading jobs…");
        new SwingWorker<List<JobEntry>, Void>() {
            @Override
            protected List<JobEntry> doInBackground() throws SQLException {
                return jobDAO.listForEmployer(employer.userId(), company.companyId());
            }

            @Override
            protected void done() {
                try {
                    tableModel.setJobs(get());
                    setBusy(false, tableModel.getRowCount() == 0
                            ? "No jobs have been posted for this company yet." : " ");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setBusy(false, " ");
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    setBusy(false, " ");
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void addJob() {
        JobEntry job = JobEditorDialog.showDialog(this, null);
        if (job != null) {
            saveJob(job, false);
        } else if (openAddJobOnReady) {
            refreshJobs();
        }
    }

    private void scheduleInitialAddJob() {
        if (initialAddJobScheduled) {
            return;
        }
        initialAddJobScheduled = true;
        SwingUtilities.invokeLater(() -> {
            if (isDisplayable() && isVisible() && UserSession.isCurrent(employer)) {
                addJob();
            }
        });
    }

    private void editJob() {
        JobEntry selected = selectedJob();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a job to edit.",
                    "No job selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setBusy(true, "Verifying job ownership…");
        new SwingWorker<JobEntry, Void>() {
            @Override
            protected JobEntry doInBackground() throws SQLException {
                return jobDAO.findForEmployer(employer.userId(), company.companyId(), selected.jobId());
            }

            @Override
            protected void done() {
                try {
                    JobEntry verified = get();
                    setBusy(false, " ");
                    if (verified == null) {
                        JOptionPane.showMessageDialog(EmployerJobManagementFrame.this,
                                "The selected job is no longer available for this company.",
                                "Job not found", JOptionPane.WARNING_MESSAGE);
                        refreshJobs();
                        return;
                    }
                    JobEntry changes = JobEditorDialog.showDialog(EmployerJobManagementFrame.this, verified);
                    if (changes != null) {
                        saveJob(changes, true);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setBusy(false, " ");
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    setBusy(false, " ");
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void saveJob(JobEntry job, boolean editing) {
        setBusy(true, editing ? "Saving job changes…" : "Creating job…");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws SQLException {
                if (editing) {
                    return jobDAO.updateForEmployer(employer.userId(), company.companyId(), job.jobId(), job);
                }
                return jobDAO.createForEmployer(employer.userId(), company.companyId(), job);
            }

            @Override
            protected void done() {
                try {
                    boolean saved = get();
                    setBusy(false, " ");
                    if (!saved) {
                        JOptionPane.showMessageDialog(EmployerJobManagementFrame.this,
                                "The job could not be saved. It may no longer belong to this company.",
                                "Job not saved", JOptionPane.WARNING_MESSAGE);
                        refreshJobs();
                        return;
                    }
                    JOptionPane.showMessageDialog(EmployerJobManagementFrame.this,
                            editing ? "Job updated." : "Job created.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                    refreshJobs();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setBusy(false, " ");
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    setBusy(false, " ");
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private void deactivateJob() {
        JobEntry selected = selectedJob();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a job to deactivate.",
                    "No job selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if ("INACTIVE".equalsIgnoreCase(selected.status())) {
            JOptionPane.showMessageDialog(this, "This job is already inactive.",
                    "Already inactive", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Deactivate ‘" + selected.jobTitle() + "’? It will remain in your job list as INACTIVE.",
                "Confirm deactivation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true, "Deactivating job…");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws SQLException {
                return jobDAO.deactivateForEmployer(employer.userId(), company.companyId(), selected.jobId());
            }

            @Override
            protected void done() {
                try {
                    boolean deactivated = get();
                    setBusy(false, " ");
                    if (!deactivated) {
                        JOptionPane.showMessageDialog(EmployerJobManagementFrame.this,
                                "The job was not found for this company.", "Job not changed",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(EmployerJobManagementFrame.this,
                                "Job deactivated.", "Updated", JOptionPane.INFORMATION_MESSAGE);
                    }
                    refreshJobs();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setBusy(false, " ");
                    showDatabaseError(exception);
                } catch (ExecutionException exception) {
                    setBusy(false, " ");
                    showDatabaseError(exception.getCause());
                }
            }
        }.execute();
    }

    private JobEntry selectedJob() {
        int row = jobsTable.getSelectedRow();
        return row < 0 ? null : tableModel.getJobAt(jobsTable.convertRowIndexToModel(row));
    }

    private void updateActionState() {
        boolean selected = jobsTable.getSelectedRow() >= 0;
        editButton.setEnabled(!busy && selected);
        deactivateButton.setEnabled(!busy && selected);
        addButton.setEnabled(!busy && company != null);
        refreshButton.setEnabled(!busy && company != null);
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        statusLabel.setText(message);
        updateActionState();
    }

    private void showDatabaseError(Throwable cause) {
        if (cause != null) {
            System.err.println("Employer job management database operation failed: "
                    + cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                System.err.printf("SQLState=%s, vendorCode=%d%n", sqlException.getSQLState(),
                        sqlException.getErrorCode());
            }
        }
        JOptionPane.showMessageDialog(this,
                "The request could not be completed. Check the database connection and try again.",
                "Database operation failed", JOptionPane.ERROR_MESSAGE);
    }

    private static final class JobTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Job ID", "Title", "Location", "Job Type", "Posted Date", "Status"};
        private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        private List<JobEntry> jobs = new ArrayList<>();

        void setJobs(List<JobEntry> jobs) {
            this.jobs = new ArrayList<>(jobs);
            fireTableDataChanged();
        }

        JobEntry getJobAt(int row) {
            return jobs.get(row);
        }

        @Override
        public int getRowCount() {
            return jobs.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            JobEntry job = jobs.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> job.jobId();
                case 1 -> job.jobTitle();
                case 2 -> job.location() == null ? "" : job.location();
                case 3 -> job.jobType() == null ? "" : job.jobType();
                case 4 -> job.postedDate() == null ? ""
                        : job.postedDate().toLocalDateTime().format(dateFormat);
                case 5 -> job.status() == null ? "" : job.status();
                default -> throw new IndexOutOfBoundsException("Unknown job table column");
            };
        }
    }
}
