import React, { useState, useEffect } from 'react';
import { XTerm } from 'xterm-for-react';
import { FitAddon } from 'xterm-addon-fit';
import '../styles/ToolOutput.css';

const UnifiedEmulator = ({ toolOutputs = [], activeToolType = 'all' }) => {
  const [selectedOutput, setSelectedOutput] = useState(null);
  const [toolTypes, setToolTypes] = useState([]);
  const [filteredOutputs, setFilteredOutputs] = useState([]);

  // Process and organize tool outputs
  useEffect(() => {
    if (!toolOutputs || toolOutputs.length === 0) {
      setFilteredOutputs([]);
      setSelectedOutput(null);
      return;
    }

    // Extract unique tool types
    const types = [...new Set(toolOutputs.map(output => output?.toolName || 'unknown'))];
    setToolTypes(['all', ...types]);

    // Filter outputs based on selected tool type
    const filtered = activeToolType === 'all' 
      ? toolOutputs 
      : toolOutputs.filter(output => output?.toolName === activeToolType);
    
    setFilteredOutputs(filtered);
    
    // Select the most recent output by default
    if (filtered.length > 0 && (!selectedOutput || !filtered.includes(selectedOutput))) {
      setSelectedOutput(filtered[filtered.length - 1]);
    }
  }, [toolOutputs, activeToolType, selectedOutput]);

  // Render the appropriate emulator based on tool type
  const renderEmulator = (output) => {
    if (!output) return renderEmptyState();
    
    try {
      switch (output.toolName) {
        case 'execute_command':
          return <TerminalOutput output={output} />;
        case 'browser_automation':
          return <BrowserOutput output={output} />;
        case 'file_system':
          return <FileSystemOutput output={output} />;
        case 'git_operations':
          return <GitOutput output={output} />;
        case 'build_tool':
          return <BuildOutput output={output} />;
        case 'code_intelligence':
          return <CodeOutput output={output} />;
        case 'data_visualization':
          return <DataVisualizationOutput output={output} />;
        default:
          return <FallbackOutput output={output} />;
      }
    } catch (error) {
      console.error('Error rendering tool output:', error);
      return <ErrorOutput error={error} output={output} />;
    }
  };

  const renderEmptyState = () => {
    return (
      <div className="empty-state">
        <div>No tool output available</div>
      </div>
    );
  };

  return (
    <div className="unified-emulator">
      <div className="emulator-header">
        <div className="tool-selector">
          <label htmlFor="tool-type-select">Tool Type:</label>
          <select 
            id="tool-type-select" 
            value={activeToolType}
            onChange={(e) => setActiveToolType(e.target.value)}
          >
            {toolTypes.map(type => (
              <option key={type} value={type}>
                {type.charAt(0).toUpperCase() + type.slice(1).replace(/_/g, ' ')}
              </option>
            ))}
          </select>
        </div>
        
        <div className="output-count">
          {filteredOutputs.length} {filteredOutputs.length === 1 ? 'output' : 'outputs'}
        </div>
      </div>
      
      <div className="emulator-content">
        {filteredOutputs.length > 0 ? (
          renderEmulator(selectedOutput || filteredOutputs[filteredOutputs.length - 1])
        ) : (
          renderEmptyState()
        )}
      </div>
      
      {filteredOutputs.length > 1 && (
        <div className="output-history">
          <h4>Output History</h4>
          <div className="history-list">
            {filteredOutputs.map((output, index) => (
              <div 
                key={index}
                className={`history-item ${selectedOutput === output ? 'selected' : ''}`}
                onClick={() => setSelectedOutput(output)}
              >
                <span className="history-tool">{output.toolName}</span>
                <span className="history-time">
                  {output.timestamp ? new Date(output.timestamp).toLocaleTimeString() : 'Unknown time'}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

const TerminalOutput = ({ output }) => {
  const fitAddon = new FitAddon();
  const xtermRef = React.useRef(null);
  
  React.useEffect(() => {
    if (xtermRef.current) {
      fitAddon.fit();
      
      // Write output to terminal safely
      try {
        if (Array.isArray(output?.output)) {
          output.output.forEach(line => {
            if (line && typeof line.content === 'string') {
              xtermRef.current.terminal.writeln(line.content);
            }
          });
        } else if (typeof output?.output === 'string') {
          output.output.split('\n').forEach(line => {
            xtermRef.current.terminal.writeln(line);
          });
        } else if (output?.output?.content && typeof output.output.content === 'string') {
          output.output.content.split('\n').forEach(line => {
            xtermRef.current.terminal.writeln(line);
          });
        } else {
          xtermRef.current.terminal.writeln('No terminal output available or invalid format');
        }
      } catch (error) {
        console.error('Error writing to terminal:', error);
        xtermRef.current.terminal.writeln(`Error displaying output: ${error.message}`);
      }
    }
  }, [output, fitAddon]);
  
  return (
    <div className="terminal-container">
      <XTerm
        ref={xtermRef}
        addons={[fitAddon]}
        options={{
          theme: {
            background: '#1e1e1e',
            foreground: '#f0f0f0',
          },
          fontFamily: 'Menlo, Monaco, "Courier New", monospace',
          fontSize: 14,
          cursorBlink: false,
          convertEol: true,
          disableStdin: true,
        }}
      />
    </div>
  );
};

const BrowserOutput = ({ output }) => {
  // Handle browser automation output with screenshots
  const getScreenshot = () => {
    try {
      if (output?.output?.metadata?.screenshot) {
        return `data:image/png;base64,${output.output.metadata.screenshot}`;
      }
      
      if (output?.output?.metadata?.after_screenshot) {
        return `data:image/png;base64,${output.output.metadata.after_screenshot}`;
      }
    } catch (error) {
      console.error('Error getting screenshot:', error);
    }
    
    return null;
  };
  
  const screenshot = getScreenshot();
  
  return (
    <div className="browser-output">
      {screenshot && (
        <div className="browser-screenshot">
          <img src={screenshot} alt="Browser screenshot" />
        </div>
      )}
      <div className="browser-info">
        <div className="browser-action">
          <strong>Action:</strong> {output?.args?.action || 'Unknown'}
        </div>
        {output?.args?.url && (
          <div className="browser-url">
            <strong>URL:</strong> {output.args.url}
          </div>
        )}
        {output?.output?.content && (
          <div className="browser-result">
            <strong>Result:</strong> {output.output.content}
          </div>
        )}
      </div>
    </div>
  );
};

const FileSystemOutput = ({ output }) => {
  return (
    <div className="file-system-output">
      <div className="operation-info">
        <strong>Operation:</strong> {output?.args?.operation || 'Unknown'}
        <br />
        <strong>Path:</strong> {output?.args?.path || 'Unknown'}
      </div>
      <div className="operation-result">
        {output?.output?.type === 'file_content' && (
          <pre className="file-content">{output.output.content || 'No content'}</pre>
        )}
        {output?.output?.type === 'directory_listing' && output?.output?.metadata?.entries && (
          <div className="directory-listing">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Size</th>
                </tr>
              </thead>
              <tbody>
                {output.output.metadata.entries.map((entry, index) => (
                  <tr key={index}>
                    <td>{entry.name}</td>
                    <td>{entry.isDirectory ? 'Directory' : 'File'}</td>
                    <td>{entry.isDirectory ? '-' : formatFileSize(entry.size || 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {(output?.output?.type === 'file_written' || 
          output?.output?.type === 'file_appended' || 
          output?.output?.type === 'file_deleted') && (
          <div className="operation-message">
            {output.output.content || 'Operation completed'}
          </div>
        )}
        {!output?.output?.type && (
          <div className="operation-message">
            {typeof output?.output === 'string' ? output.output : 'Operation completed'}
          </div>
        )}
      </div>
    </div>
  );
};

const GitOutput = ({ output }) => {
  return (
    <div className="git-output">
      <div className="git-operation">
        <strong>Operation:</strong> {output?.args?.operation || 'Unknown'}
        <br />
        <strong>Repository:</strong> {output?.args?.path || 'Unknown'}
      </div>
      {output?.output?.type === 'git_status' && output?.output?.metadata && (
        <div className="git-status">
          <h4>Status</h4>
          <div className="status-section">
            <h5>Modified</h5>
            <ul>
              {output.output.metadata.modified?.map((file, index) => (
                <li key={index}>{file}</li>
              )) || <li>None</li>}
            </ul>
          </div>
          <div className="status-section">
            <h5>Untracked</h5>
            <ul>
              {output.output.metadata.untracked?.map((file, index) => (
                <li key={index}>{file}</li>
              )) || <li>None</li>}
            </ul>
          </div>
        </div>
      )}
      {output?.output?.type === 'git_log' && output?.output?.metadata?.commits && (
        <div className="git-log">
          <h4>Recent Commits</h4>
          <ul className="commit-list">
            {output.output.metadata.commits.map((commit, index) => (
              <li key={index} className="commit-item">
                <div className="commit-id">{commit.id?.substring(0, 7) || 'Unknown'}</div>
                <div className="commit-message">{commit.message || 'No message'}</div>
                <div className="commit-author">{commit.author || 'Unknown author'}</div>
                <div className="commit-date">{commit.date || 'Unknown date'}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
      {(output?.output?.type === 'git_init' || 
        output?.output?.type === 'git_clone' || 
        output?.output?.type === 'git_commit' || 
        output?.output?.type === 'git_branch') && (
        <div className="git-result">
          {output.output.content || 'Operation completed'}
        </div>
      )}
      {!output?.output?.type && (
        <div className="git-result">
          {typeof output?.output === 'string' ? output.output : 
           output?.output?.content ? output.output.content : 'Operation completed'}
        </div>
      )}
    </div>
  );
};

const BuildOutput = ({ output }) => {
  return (
    <div className="build-output">
      <div className="build-info">
        <strong>Tool:</strong> {output?.args?.tool || 'Unknown'}
        <br />
        <strong>Project:</strong> {output?.args?.projectPath || 'Unknown'}
        <br />
        <strong>Goals:</strong> {Array.isArray(output?.args?.goals) ? output.args.goals.join(', ') : 'Unknown'}
      </div>
      <div className="build-result">
        <pre>{Array.isArray(output?.output) 
          ? output.output.map(line => line?.content || '').join('\n') 
          : output?.output?.content || 
            (typeof output?.output === 'string' ? output.output : 'No output')}</pre>
      </div>
    </div>
  );
};

const CodeOutput = ({ output }) => {
  return (
    <div className="code-output">
      <div className="code-operation">
        <strong>Operation:</strong> {output?.args?.operation || 'Unknown'}
        <br />
        <strong>Path:</strong> {output?.args?.path || 'Unknown'}
        {output?.args?.query && (
          <>
            <br />
            <strong>Query:</strong> {output.args.query}
          </>
        )}
      </div>
      {output?.output?.type === 'analysis_result' && output?.output?.metadata && (
        <div className="code-analysis">
          <h4>Analysis Result</h4>
          {output.output.metadata.classes && (
            <div className="analysis-section">
              <h5>Classes</h5>
              <ul>
                {output.output.metadata.classes.map((cls, index) => (
                  <li key={index}>{cls}</li>
                ))}
              </ul>
            </div>
          )}
          {output.output.metadata.methods && (
            <div className="analysis-section">
              <h5>Methods</h5>
              <ul>
                {output.output.metadata.methods.map((method, index) => (
                  <li key={index}>{method}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
      {output?.output?.type === 'method_search_result' && output?.output?.metadata?.methods && (
        <div className="method-search">
          <h4>Method Search Results</h4>
          <ul className="method-list">
            {output.output.metadata.methods.map((method, index) => (
              <li key={index} className="method-item">
                <div className="method-name">{method.name || 'Unknown'}</div>
                <div className="method-return">{method.returnType || 'Unknown'}</div>
                <div className="method-file">{method.file || 'Unknown'}:{method.line || '?'}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
      {!output?.output?.type && (
        <div className="code-result">
          <pre>{typeof output?.output === 'string' ? output.output : 
                output?.output?.content ? output.output.content : 'No output'}</pre>
        </div>
      )}
    </div>
  );
};

const DataVisualizationOutput = ({ output }) => {
  return (
    <div className="data-viz-output">
      <div className="chart-info">
        <strong>Chart Type:</strong> {output?.args?.chartType || 'Unknown'}
        <br />
        <strong>Title:</strong> {output?.args?.title || 'Unknown'}
      </div>
      {output?.output?.metadata?.imageBase64 && (
        <div className="chart-image">
          <img 
            src={`data:image/${output?.args?.outputPath?.endsWith('.png') ? 'png' : 'jpeg'};base64,${output.output.metadata.imageBase64}`} 
            alt={output?.args?.title || 'Chart'} 
          />
        </div>
      )}
      {!output?.output?.metadata?.imageBase64 && (
        <div className="chart-fallback">
          <p>No visualization data available</p>
          <pre>{typeof output?.output === 'string' ? output.output : 
                output?.output?.content ? output.output.content : 'No output'}</pre>
        </div>
      )}
    </div>
  );
};

const FallbackOutput = ({ output }) => {
  // Try to determine if we should use terminal emulation
  const shouldUseTerminal = () => {
    if (typeof output?.output === 'string' && output.output.includes('\n')) {
      return true;
    }
    if (Array.isArray(output?.output)) {
      return true;
    }
    if (output?.output?.content && typeof output.output.content === 'string' && 
        output.output.content.length > 100) {
      return true;
    }
    return false;
  };

  if (shouldUseTerminal()) {
    return <TerminalOutput output={output} />;
  }

  return (
    <div className="fallback-output">
      <div className="tool-info">
        <strong>Tool:</strong> {output?.toolName || 'Unknown'}
        <br />
        <strong>Operation:</strong> {output?.args?.operation || 'Unknown'}
      </div>
      <div className="output-content">
        {typeof output?.output === 'string' ? (
          <pre>{output.output}</pre>
        ) : output?.output?.content ? (
          <pre>{output.output.content}</pre>
        ) : (
          <pre>{JSON.stringify(output, null, 2)}</pre>
        )}
      </div>
    </div>
  );
};

const ErrorOutput = ({ error, output }) => {
  return (
    <div className="error-output">
      <div className="error-header">
        <h4>Error Rendering Tool Output</h4>
      </div>
      <div className="error-message">
        <p>{error.message}</p>
      </div>
      <div className="error-details">
        <h5>Tool Details</h5>
        <p><strong>Tool:</strong> {output?.toolName || 'Unknown'}</p>
        <p><strong>Operation:</strong> {output?.args?.operation || 'Unknown'}</p>
      </div>
      <div className="raw-output">
        <h5>Raw Output</h5>
        <pre>{JSON.stringify(output, null, 2)}</pre>
      </div>
    </div>
  );
};

// Helper function to format file size
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

export default UnifiedEmulator;
