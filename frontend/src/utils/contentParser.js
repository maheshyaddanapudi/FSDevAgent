/**
 * Content Parser Utility for AI Agent Messages
 * Parses and structures different types of content in agent responses
 */

export const parseAgentContent = (content) => {
  if (!content || typeof content !== 'string') {
    return { mainResponse: content || '', sections: [] };
  }

  const sections = [];
  let mainResponse = '';
  
  // Split content by common section markers - handle both # and ## patterns
  // Also split on tool_use and EVENT patterns
  const parts = content.split(/(?=#{1,2}\s*(?:THINK|REASON|ACT|OBSERVE)|<tool_use>|EVENT:)/);
  
  for (let i = 0; i < parts.length; i++) {
    let part = parts[i].trim();
    if (!part) continue;

    // Handle cases where sections are concatenated without proper spacing
    // Look for patterns like "some text## SECTION" and split them
    const concatenatedMatch = part.match(/^(.*?)(#{1,2}\s*(?:THINK|REASON|ACT|OBSERVE).*)$/s);
    if (concatenatedMatch && concatenatedMatch[1].trim() && !concatenatedMatch[1].startsWith('#')) {
      // Split the concatenated content
      const beforeSection = concatenatedMatch[1].trim();
      const sectionPart = concatenatedMatch[2].trim();
      
      // Add the content before the section to main response or previous section
      if (mainResponse) {
        mainResponse += '\n\n' + beforeSection;
      } else {
        mainResponse = beforeSection;
      }
      
      // Process the section part
      part = sectionPart;
    }

    // Main response (first part without section markers)
    if (i === 0 && !part.match(/^#{1,2}\s*(?:THINK|REASON|ACT|OBSERVE)/) && !part.startsWith('<tool_use>') && !part.startsWith('EVENT:')) {
      mainResponse = part;
      continue;
    }

    // Thinking sections
    if (part.match(/^#{1,2}\s*THINK/)) {
      let content = part.replace(/^#{1,2}\s*THINK\s*/, '').trim();
      // Remove any EVENT lines from the content
      content = content.replace(/EVENT:[^\n\r]*/g, '').trim();
      sections.push({
        type: 'thinking',
        title: 'Agent Thinking',
        content: content,
        icon: '🤔',
        collapsible: true,
        defaultExpanded: false
      });
      continue;
    }

    // Reasoning sections
    if (part.match(/^#{1,2}\s*REASON/)) {
      let content = part.replace(/^#{1,2}\s*REASON\s*/, '').trim();
      // Remove any EVENT lines from the content
      content = content.replace(/EVENT:[^\n\r]*/g, '').trim();
      sections.push({
        type: 'reasoning',
        title: 'Reasoning Process',
        content: content,
        icon: '🧠',
        collapsible: true,
        defaultExpanded: false
      });
      continue;
    }

    // Action sections
    if (part.match(/^#{1,2}\s*ACT/)) {
      let content = part.replace(/^#{1,2}\s*ACT\s*/, '').trim();
      // Remove any EVENT lines from the content
      content = content.replace(/EVENT:[^\n\r]*/g, '').trim();
      sections.push({
        type: 'action',
        title: 'Taking Action',
        content: content,
        icon: '⚡',
        collapsible: true,
        defaultExpanded: true
      });
      continue;
    }

    // Observation sections
    if (part.match(/^#{1,2}\s*OBSERVE/)) {
      let content = part.replace(/^#{1,2}\s*OBSERVE\s*/, '').trim();
      // Remove any EVENT lines from the content
      content = content.replace(/EVENT:[^\n\r]*/g, '').trim();
      sections.push({
        type: 'observation',
        title: 'Observation',
        content: content,
        icon: '👁️',
        collapsible: true,
        defaultExpanded: true
      });
      continue;
    }

    // Tool use sections
    if (part.includes('<tool_use>') || part.includes('"name":')) {
      const toolContent = parseToolUse(part);
      if (toolContent) {
        sections.push({
          type: 'tool_use',
          title: `Tool: ${toolContent.name}`,
          content: toolContent,
          icon: '🔧',
          collapsible: true,
          defaultExpanded: false
        });
      }
      continue;
    }

    // Event sections - handle multiple events in one part
    if (part.startsWith('EVENT:')) {
      // Split multiple events that might be in the same part
      const eventLines = part.split(/(?=EVENT:)/).filter(line => line.trim());
      
      for (const eventLine of eventLines) {
        const eventContent = parseEvent(eventLine.trim());
        if (eventContent) {
          sections.push({
            type: 'event',
            title: eventContent.title,
            content: eventContent,
            icon: eventContent.icon,
            collapsible: false,
            defaultExpanded: true
          });
        }
      }
      continue;
    }

    // If it doesn't match any pattern, add to main response
    if (mainResponse) {
      mainResponse += '\n\n' + part;
    } else {
      mainResponse = part;
    }
  }

  // Extract any remaining EVENT lines from the entire content and add them as separate sections
  const allEventMatches = content.match(/EVENT:[^\n\r]*/g) || [];
  allEventMatches.forEach(eventLine => {
    const eventContent = parseEvent(eventLine.trim());
    if (eventContent) {
      // Check if this event is already added to avoid duplicates
      const existingEvent = sections.find(section => 
        section.type === 'event' && 
        section.content.type === eventContent.type &&
        section.content.data === eventContent.data
      );
      
      if (!existingEvent) {
        sections.push({
          type: 'event',
          title: eventContent.title,
          content: eventContent,
          icon: eventContent.icon,
          collapsible: false,
          defaultExpanded: true
        });
      }
    }
  });

  return {
    mainResponse: mainResponse.trim(),
    sections: sections
  };
};
const parseToolUse = (content) => {
  try {
    // First, try to extract JSON from <tool_use> tags with flexible whitespace
    const toolUseMatch = content.match(/<tool_use>\s*(\{.*?\})\s*<\/tool_use>/s);
    if (toolUseMatch) {
      const toolData = JSON.parse(toolUseMatch[1]);
      return {
        name: toolData.name || 'Unknown Tool',
        args: toolData.args || toolData,
        rawContent: content
      };
    }

    // Also try without closing tag (in case it's malformed)
    const openTagMatch = content.match(/<tool_use>\s*(\{.*?\})/s);
    if (openTagMatch) {
      const toolData = JSON.parse(openTagMatch[1]);
      return {
        name: toolData.name || 'Unknown Tool',
        args: toolData.args || toolData,
        rawContent: content
      };
    }

    // If no tool_use tags, look for JSON with proper brace matching
    let jsonStr = '';
    let braceCount = 0;
    let startIndex = -1;
    
    // Find the start of JSON (look for opening brace followed by "name")
    for (let i = 0; i < content.length; i++) {
      if (content[i] === '{') {
        // Check if this looks like the start of our JSON (contains "name" within reasonable distance)
        const nextPortion = content.substring(i, i + 100);
        if (nextPortion.includes('"name"')) {
          startIndex = i;
          break;
        }
      }
    }
    
    if (startIndex !== -1) {
      // Extract JSON with proper brace matching
      for (let i = startIndex; i < content.length; i++) {
        const char = content[i];
        jsonStr += char;
        
        if (char === '{') {
          braceCount++;
        } else if (char === '}') {
          braceCount--;
          if (braceCount === 0) {
            break; // Found complete JSON object
          }
        }
      }
      
      if (jsonStr && braceCount === 0) {
        const toolData = JSON.parse(jsonStr);
        return {
          name: toolData.name || 'Unknown Tool',
          args: toolData.args || toolData,
          rawContent: content
        };
      }
    }
  } catch (e) {
    console.warn('Failed to parse tool use JSON:', e);
    // If JSON parsing fails, extract tool name from content
    const nameMatch = content.match(/"name":\s*"([^"]+)"/);
    return {
      name: nameMatch ? nameMatch[1] : 'Tool Execution',
      args: 'See details below',
      rawContent: content
    };
  }
  return null;
};

const parseEvent = (content) => {
  const eventMatch = content.match(/EVENT:([^:]+):?(.*)/);
  if (!eventMatch) return null;

  const eventType = eventMatch[1].trim();
  const eventData = eventMatch[2].trim();

  const eventConfig = {
    'TASK_COMPLETE': { title: 'Task Completed', icon: '✅' },
    'PROGRESS': { title: 'Progress Update', icon: '📊' },
    'PHASE_TRANSITION': { title: 'Phase Change', icon: '🔄' },
    'ERROR': { title: 'Error Occurred', icon: '❌' },
    'WARNING': { title: 'Warning', icon: '⚠️' }
  };

  const config = eventConfig[eventType] || { title: eventType, icon: '📢' };

  return {
    type: eventType,
    title: config.title,
    icon: config.icon,
    data: eventData,
    timestamp: new Date().toISOString()
  };
};

export const formatToolArgs = (args) => {
  if (typeof args === 'string') return args;
  if (typeof args === 'object') {
    return Object.entries(args)
      .map(([key, value]) => `${key}: ${typeof value === 'string' ? value : JSON.stringify(value)}`)
      .join('\n');
  }
  return JSON.stringify(args, null, 2);
};

