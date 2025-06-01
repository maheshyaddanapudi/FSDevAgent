// frontend/src/components/UnifiedEmulator/tools/FileSystemOutput.js
import React, { useState, useEffect } from 'react';
import { Tree } from 'react-arborist';
import { useEmulatorStore } from '../../../store/emulatorStore';
import './FileSystemOutput.css';

/**
 * FileSystem output visualization component
 * Renders file listings and content in a tree view with preview panel
 */
const FileSystemOutput = ({ data, allOutputs, wsConnected, toolId }) => {
  const [treeData, setTreeData] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);
  const [fileContent, setFileContent] = useState('');
  const [viewMode, setViewMode] = useState('tree');
  
  const { updateToolActivity } = useEmulatorStore();
  
  // Update tool activity
  useEffect(() => {
    if (data) {
      updateToolActivity(toolId);
    }
  }, [data, toolId, updateToolActivity]);
  
  // Process file system output
  useEffect(() => {
    if (!data) return;
    
    const output = data.output || data.data || data.content || '';
    const outputStr = typeof output === 'string' ? output : JSON.stringify(output, null, 2);
    
    // Try to detect if this is a file listing or file content
    if (outputStr.includes('total') && (outputStr.includes('drwx') || outputStr.includes('-rw-'))) {
      // This looks like a directory listing (ls -l output)
      try {
        const fileNodes = parseDirectoryListing(outputStr);
        setTreeData(fileNodes);
        setViewMode('tree');
      } catch (error) {
        console.error('Error parsing directory listing:', error);
        setFileContent(outputStr);
        setViewMode('content');
      }
    } else if (outputStr.startsWith('<?xml') || outputStr.startsWith('<!DOCTYPE') || 
               outputStr.startsWith('{') || outputStr.startsWith('[') ||
               outputStr.includes('function') || outputStr.includes('class') ||
               outputStr.includes('#include') || outputStr.includes('import ')) {
      // This looks like file content
      setFileContent(outputStr);
      setViewMode('content');
    } else {
      // Default to showing as content
      setFileContent(outputStr);
      setViewMode('content');
    }
  }, [data]);
  
  // Parse directory listing (ls -l format)
  const parseDirectoryListing = (listing) => {
    const lines = listing.split('\n').filter(line => line.trim());
    
    // Skip the first line if it contains "total"
    const startIndex = lines[0].includes('total') ? 1 : 0;
    
    return lines.slice(startIndex).map((line, index) => {
      const parts = line.trim().split(/\s+/);
      
      // Extract permissions from the first part
      const permissions = parts[0];
      const isDirectory = permissions.startsWith('d');
      
      // The filename is usually the last part
      const name = parts.slice(8).join(' ') || `file_${index}`;
      
      // Extract size (usually the 5th part)
      const size = parts[4] || '0';
      
      // Extract date (usually parts 5-7)
      const date = parts.slice(5, 8).join(' ');
      
      return {
        id: `${index}-${name}`,
        name,
        isDirectory,
        permissions,
        size,
        date,
        children: []
      };
    });
  };
  
  // Handle node selection in tree
  const handleNodeSelect = (node) => {
    setSelectedFile(node.data);
    
    // If we have file content for this node, show it
    if (!node.data.isDirectory) {
      // In a real implementation, we would fetch the file content here
      // For now, we'll just show a placeholder
      setFileContent(`Content of ${node.data.name} would be displayed here.`);
      setViewMode('content');
    }
  };
  
  // Render tree node
  const renderTreeNode = ({ node, style, dragHandle }) => {
    const { isDirectory, name, permissions, size } = node.data;
    
    return (
      <div 
        className={`file-node ${isDirectory ? 'directory' : 'file'} ${node.isSelected ? 'selected' : ''}`}
        style={style} 
        ref={dragHandle}
        onClick={() => handleNodeSelect(node)}
      >
        <span className={`file-icon ${isDirectory ? 'directory-icon' : getFileIconClass(name)}`}></span>
        <span className="file-name">{name}</span>
        <span className="file-meta">
          <span className="file-permissions">{permissions}</span>
          <span className="file-size">{size}</span>
        </span>
      </div>
    );
  };
  
  // Get icon class based on file extension
  const getFileIconClass = (filename) => {
    const ext = filename.split('.').pop().toLowerCase();
    
    switch (ext) {
      case 'js':
      case 'jsx':
      case 'ts':
      case 'tsx':
        return 'js-icon';
      case 'html':
      case 'htm':
        return 'html-icon';
      case 'css':
      case 'scss':
      case 'sass':
        return 'css-icon';
      case 'json':
        return 'json-icon';
      case 'md':
        return 'md-icon';
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
      case 'svg':
        return 'img-icon';
      case 'pdf':
        return 'pdf-icon';
      default:
        return 'file-icon';
    }
  };
  
  // Render file content with syntax highlighting
  const renderFileContent = () => {
    if (!fileContent) {
      return (
        <div className="file-empty-content">
          No file content to display
        </div>
      );
    }
    
    return (
      <pre className="file-content-preview">
        {fileContent}
      </pre>
    );
  };
  
  // Toggle between tree and content views
  const toggleViewMode = () => {
    setViewMode(viewMode === 'tree' ? 'content' : 'tree');
  };
  
  return (
    <div className="filesystem-output">
      <div className="filesystem-toolbar">
        <div className="filesystem-breadcrumb">
          {selectedFile ? (
            <span className="filesystem-path">
              {selectedFile.name}
            </span>
          ) : (
            <span className="filesystem-path">
              /
            </span>
          )}
        </div>
        
        <div className="filesystem-actions">
          <button 
            className={`filesystem-view-toggle ${viewMode === 'tree' ? 'active' : ''}`}
            onClick={() => setViewMode('tree')}
            title="Show file tree"
          >
            🗂️
          </button>
          
          <button 
            className={`filesystem-view-toggle ${viewMode === 'content' ? 'active' : ''}`}
            onClick={() => setViewMode('content')}
            title="Show file content"
          >
            📄
          </button>
          
          <span className="filesystem-status">
            {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
          </span>
        </div>
      </div>
      
      <div className="filesystem-content">
        {viewMode === 'tree' && treeData.length > 0 ? (
          <div className="filesystem-tree">
            <Tree
              data={treeData}
              renderNode={renderTreeNode}
              width="100%"
              height={500} // This will be overridden by CSS
              indent={20}
              rowHeight={28}
              overscanCount={10}
            />
          </div>
        ) : viewMode === 'content' ? (
          <div className="filesystem-preview">
            {renderFileContent()}
          </div>
        ) : (
          <div className="filesystem-empty-state">
            <div className="filesystem-empty-icon">🔄</div>
            <div className="filesystem-empty-message">Waiting for file system output...</div>
          </div>
        )}
      </div>
    </div>
  );
};

export default FileSystemOutput;
