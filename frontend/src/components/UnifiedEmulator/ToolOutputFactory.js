// frontend/src/components/UnifiedEmulator/ToolOutputFactory.js
import React, { lazy, Suspense } from 'react';
import ToolSkeleton from './ToolSkeleton';

/**
 * Dynamic tool component loader
 * Lazily loads the appropriate tool visualization component based on type
 */
const ToolOutputFactory = ({ type, data, allOutputs, wsConnected, toolId }) => {
  // Define lazy-loaded tool components
  const toolComponents = {
    initializing: lazy(() => import('./tools/InitializingOutput')),
    terminal: lazy(() => import('./tools/TerminalOutput')),
    browser: lazy(() => import('./tools/BrowserOutput')),
    filesystem: lazy(() => import('./tools/FileSystemOutput')),
    git: lazy(() => import('./tools/GitOutput')),
    build: lazy(() => import('./tools/BuildOutput')),
    code: lazy(() => import('./tools/CodeOutput')),
    dataviz: lazy(() => import('./tools/DataVizOutput'))
  };

  // Get the appropriate component for the tool type
  const ToolComponent = toolComponents[type] || toolComponents.terminal;

  return (
    <Suspense fallback={<ToolSkeleton type={type} />}>
      <ToolComponent 
        data={data} 
        allOutputs={allOutputs} 
        wsConnected={wsConnected}
        toolId={toolId}
      />
    </Suspense>
  );
};

export default ToolOutputFactory;
