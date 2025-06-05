import React from 'react';
import '../styles/ToolOutput.css';

const PlanningToolOutput = ({ output }) => {
  // Extract the operation type from the output
  const operationType = output?.output?.type || 'unknown';
  
  const renderPlanContent = () => {
    if (!output?.output) return <p>No output available</p>;
    
    switch (operationType) {
      case 'plan_created':
        return <PlanCreatedOutput output={output} />;
      case 'task_decomposed':
        return <TaskDecomposedOutput output={output} />;
      case 'dependencies_analyzed':
        return <DependenciesAnalyzedOutput output={output} />;
      case 'critical_path_calculated':
        return <CriticalPathOutput output={output} />;
      case 'sprint_planned':
        return <SprintPlannedOutput output={output} />;
      case 'technical_debt_tracked':
        return <TechnicalDebtOutput output={output} />;
      case 'architecture_decision_created':
        return <ArchitectureDecisionOutput output={output} />;
      case 'plan_retrieved':
        return <PlanRetrievedOutput output={output} />;
      case 'task_updated':
        return <TaskUpdatedOutput output={output} />;
      case 'report_generated':
        return <ReportGeneratedOutput output={output} />;
      default:
        return <GenericPlanningOutput output={output} />;
    }
  };
  
  return (
    <div className="planning-tool-output">
      <div className="planning-operation">
        <strong>Operation:</strong> {output?.args?.operation || 'Unknown'}
      </div>
      <div className="planning-result">
        {renderPlanContent()}
      </div>
    </div>
  );
};

const PlanCreatedOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="plan-created">
      <h4>Plan Created</h4>
      <div className="plan-details">
        <div className="plan-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="plan-detail"><strong>Objective:</strong> {metadata.objective || 'Unknown'}</div>
        <div className="plan-detail"><strong>Type:</strong> {metadata.type || 'Unknown'}</div>
        <div className="plan-detail"><strong>Status:</strong> {metadata.status || 'Unknown'}</div>
        <div className="plan-detail"><strong>Created:</strong> {metadata.created_at || 'Unknown'}</div>
        <div className="plan-detail"><strong>Tasks:</strong> {metadata.task_count || '0'}</div>
      </div>
      <div className="plan-message">
        {output?.output?.content || 'Plan created successfully'}
      </div>
    </div>
  );
};

const TaskDecomposedOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="task-decomposed">
      <h4>Task Decomposed</h4>
      <div className="task-details">
        <div className="task-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="task-detail"><strong>Task ID:</strong> {metadata.task_id || 'Unknown'}</div>
        <div className="task-detail"><strong>Subtasks:</strong> {metadata.subtask_count || '0'}</div>
      </div>
      {metadata.subtasks && (
        <div className="subtasks-list">
          <h5>Subtasks:</h5>
          <ul>
            {metadata.subtasks.split(', ').map((subtask, index) => (
              <li key={index}>{subtask}</li>
            ))}
          </ul>
        </div>
      )}
      <div className="task-message">
        {output?.output?.content || 'Task decomposed successfully'}
      </div>
    </div>
  );
};

const DependenciesAnalyzedOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="dependencies-analyzed">
      <h4>Dependencies Analyzed</h4>
      <div className="dependencies-details">
        <div className="dependency-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="dependency-detail"><strong>Dependencies:</strong> {metadata.dependency_count || '0'}</div>
      </div>
      {metadata.dependency_graph && (
        <div className="dependency-graph">
          <h5>Dependency Graph:</h5>
          <pre>{metadata.dependency_graph}</pre>
        </div>
      )}
      <div className="dependencies-message">
        {output?.output?.content || 'Dependencies analyzed successfully'}
      </div>
    </div>
  );
};

const CriticalPathOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="critical-path">
      <h4>Critical Path Calculated</h4>
      <div className="critical-path-details">
        <div className="critical-path-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="critical-path-detail"><strong>Path Length:</strong> {metadata.path_length || '0'}</div>
        <div className="critical-path-detail"><strong>Estimated Duration:</strong> {metadata.estimated_duration || 'Unknown'}</div>
      </div>
      {metadata.critical_path && (
        <div className="path-tasks">
          <h5>Critical Path Tasks:</h5>
          <ol>
            {metadata.critical_path.split(', ').map((task, index) => (
              <li key={index}>{task}</li>
            ))}
          </ol>
        </div>
      )}
      <div className="critical-path-message">
        {output?.output?.content || 'Critical path calculated successfully'}
      </div>
    </div>
  );
};

const SprintPlannedOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="sprint-planned">
      <h4>Sprint Planned</h4>
      <div className="sprint-details">
        <div className="sprint-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="sprint-detail"><strong>Sprint:</strong> {metadata.sprint_name || 'Unknown'}</div>
        <div className="sprint-detail"><strong>Start Date:</strong> {metadata.start_date || 'Unknown'}</div>
        <div className="sprint-detail"><strong>End Date:</strong> {metadata.end_date || 'Unknown'}</div>
        <div className="sprint-detail"><strong>Tasks:</strong> {metadata.task_count || '0'}</div>
      </div>
      {metadata.tasks && (
        <div className="sprint-tasks">
          <h5>Sprint Tasks:</h5>
          <ul>
            {metadata.tasks.split(', ').map((task, index) => (
              <li key={index}>{task}</li>
            ))}
          </ul>
        </div>
      )}
      <div className="sprint-message">
        {output?.output?.content || 'Sprint planned successfully'}
      </div>
    </div>
  );
};

const TechnicalDebtOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="technical-debt">
      <h4>Technical Debt Tracked</h4>
      <div className="debt-details">
        <div className="debt-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="debt-detail"><strong>Debt Items:</strong> {metadata.debt_count || '0'}</div>
      </div>
      {metadata.debt_items && (
        <div className="debt-items">
          <h5>Technical Debt Items:</h5>
          <ul>
            {metadata.debt_items.map((item, index) => (
              <li key={index}>
                <div><strong>{item.title}</strong></div>
                <div>{item.description}</div>
                <div><em>Priority: {item.priority}</em></div>
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="debt-message">
        {output?.output?.content || 'Technical debt tracked successfully'}
      </div>
    </div>
  );
};

const ArchitectureDecisionOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="architecture-decision">
      <h4>Architecture Decision Created</h4>
      <div className="decision-details">
        <div className="decision-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="decision-detail"><strong>Decision ID:</strong> {metadata.decision_id || 'Unknown'}</div>
        <div className="decision-detail"><strong>Title:</strong> {metadata.title || 'Unknown'}</div>
      </div>
      <div className="decision-content">
        <h5>Decision:</h5>
        <div className="decision-section"><strong>Context:</strong> {metadata.context || 'Not provided'}</div>
        <div className="decision-section"><strong>Decision:</strong> {metadata.decision || 'Not provided'}</div>
        <div className="decision-section"><strong>Consequences:</strong> {metadata.consequences || 'Not provided'}</div>
      </div>
      <div className="decision-message">
        {output?.output?.content || 'Architecture decision created successfully'}
      </div>
    </div>
  );
};

const PlanRetrievedOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  const plan = metadata.plan || {};
  
  return (
    <div className="plan-retrieved">
      <h4>Plan Retrieved</h4>
      <div className="plan-details">
        <div className="plan-detail"><strong>Plan ID:</strong> {plan.id || 'Unknown'}</div>
        <div className="plan-detail"><strong>Objective:</strong> {plan.objective || 'Unknown'}</div>
        <div className="plan-detail"><strong>Type:</strong> {plan.type || 'Unknown'}</div>
        <div className="plan-detail"><strong>Status:</strong> {plan.status || 'Unknown'}</div>
        <div className="plan-detail"><strong>Created:</strong> {plan.createdAt || 'Unknown'}</div>
        <div className="plan-detail"><strong>Updated:</strong> {plan.updatedAt || 'Unknown'}</div>
      </div>
      {plan.tasks && plan.tasks.length > 0 && (
        <div className="plan-tasks">
          <h5>Tasks:</h5>
          <ul>
            {plan.tasks.map((task, index) => (
              <li key={index}>
                <div><strong>{task.title}</strong> ({task.status})</div>
                <div>{task.description}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="plan-message">
        {output?.output?.content || 'Plan retrieved successfully'}
      </div>
    </div>
  );
};

const TaskUpdatedOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="task-updated">
      <h4>Task Updated</h4>
      <div className="task-details">
        <div className="task-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="task-detail"><strong>Task ID:</strong> {metadata.task_id || 'Unknown'}</div>
        <div className="task-detail"><strong>Title:</strong> {metadata.title || 'Unknown'}</div>
        <div className="task-detail"><strong>Status:</strong> {metadata.status || 'Unknown'}</div>
      </div>
      <div className="update-details">
        <h5>Updates:</h5>
        <ul>
          {metadata.updates && Object.entries(metadata.updates).map(([key, value], index) => (
            <li key={index}><strong>{key}:</strong> {value}</li>
          ))}
        </ul>
      </div>
      <div className="task-message">
        {output?.output?.content || 'Task updated successfully'}
      </div>
    </div>
  );
};

const ReportGeneratedOutput = ({ output }) => {
  const metadata = output?.output?.metadata || {};
  
  return (
    <div className="report-generated">
      <h4>Report Generated</h4>
      <div className="report-details">
        <div className="report-detail"><strong>Plan ID:</strong> {metadata.plan_id || 'Unknown'}</div>
        <div className="report-detail"><strong>Format:</strong> {metadata.format || 'Unknown'}</div>
      </div>
      {metadata.report_content && (
        <div className="report-content">
          <h5>Report:</h5>
          {metadata.format === 'markdown' ? (
            <div className="markdown-report">
              <pre>{metadata.report_content}</pre>
            </div>
          ) : metadata.format === 'gantt' ? (
            <div className="gantt-report">
              <pre>{metadata.report_content}</pre>
            </div>
          ) : (
            <pre>{metadata.report_content}</pre>
          )}
        </div>
      )}
      <div className="report-message">
        {output?.output?.content || 'Report generated successfully'}
      </div>
    </div>
  );
};

const GenericPlanningOutput = ({ output }) => {
  return (
    <div className="generic-planning-output">
      <h4>Planning Tool Output</h4>
      <div className="output-content">
        {output?.output?.content || 'No content available'}
      </div>
      {output?.output?.metadata && (
        <div className="output-metadata">
          <h5>Metadata:</h5>
          <pre>{JSON.stringify(output.output.metadata, null, 2)}</pre>
        </div>
      )}
    </div>
  );
};

export default PlanningToolOutput;
