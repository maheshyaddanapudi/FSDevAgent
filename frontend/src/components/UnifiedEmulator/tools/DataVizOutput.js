// frontend/src/components/UnifiedEmulator/tools/DataVizOutput.js
import React, { useState, useEffect } from 'react';
import { LineChart, Line, BarChart, Bar, PieChart, Pie, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Cell } from 'recharts';
import { useEmulatorStore } from '../../../store/emulatorStore';
import './DataVizOutput.css';

/**
 * Data visualization output component
 * Renders charts and graphs for data visualization tool outputs
 */
const DataVizOutput = ({ data, allOutputs, wsConnected, toolId }) => {
  const [chartType, setChartType] = useState('auto');
  const [chartData, setChartData] = useState([]);
  const [chartConfig, setChartConfig] = useState({});
  const [rawData, setRawData] = useState('');
  
  const { updateToolActivity } = useEmulatorStore();
  
  // Update tool activity
  useEffect(() => {
    if (data) {
      updateToolActivity(toolId);
    }
  }, [data, toolId, updateToolActivity]);
  
  // Process data visualization output
  useEffect(() => {
    if (!data) return;
    
    const output = data.output || data.data || data.content || '';
    
    // Store raw data for display
    setRawData(typeof output === 'string' ? output : JSON.stringify(output, null, 2));
    
    try {
      // Try to parse the data for visualization
      let parsedData;
      let detectedType = 'auto';
      let config = {};
      
      if (typeof output === 'string') {
        // Try to parse JSON string
        try {
          parsedData = JSON.parse(output);
        } catch (e) {
          // If not JSON, try to parse CSV-like format
          parsedData = parseCSVLikeData(output);
        }
      } else if (typeof output === 'object') {
        parsedData = output;
      }
      
      // Process the parsed data
      if (parsedData) {
        // Check if data has specific chart configuration
        if (parsedData.type) {
          detectedType = parsedData.type;
        } else if (parsedData.chartType) {
          detectedType = parsedData.chartType;
        }
        
        // Extract chart data
        let dataArray = [];
        
        if (Array.isArray(parsedData)) {
          dataArray = parsedData;
        } else if (parsedData.data && Array.isArray(parsedData.data)) {
          dataArray = parsedData.data;
        } else if (typeof parsedData === 'object') {
          // Try to convert object to array format
          dataArray = Object.entries(parsedData).map(([key, value]) => ({
            name: key,
            value: typeof value === 'number' ? value : 0
          }));
        }
        
        // Auto-detect chart type if not specified
        if (detectedType === 'auto') {
          detectedType = detectChartType(dataArray);
        }
        
        // Extract chart configuration
        if (parsedData.config) {
          config = parsedData.config;
        }
        
        setChartData(dataArray);
        setChartType(detectedType);
        setChartConfig(config);
      }
    } catch (error) {
      console.error('Error processing data visualization output:', error);
    }
  }, [data]);
  
  // Parse CSV-like data
  const parseCSVLikeData = (text) => {
    const lines = text.trim().split('\n');
    const headers = lines[0].split(',').map(h => h.trim());
    
    return lines.slice(1).map(line => {
      const values = line.split(',').map(v => v.trim());
      const row = {};
      
      headers.forEach((header, index) => {
        const value = values[index];
        // Try to convert to number if possible
        row[header] = isNaN(value) ? value : parseFloat(value);
      });
      
      return row;
    });
  };
  
  // Auto-detect chart type based on data structure
  const detectChartType = (data) => {
    if (!data || data.length === 0) return 'bar';
    
    // Check if data has time-series-like structure
    const hasTimePattern = data.some(item => 
      item.date || 
      item.time || 
      (item.name && (
        String(item.name).includes('/') || 
        String(item.name).includes('-') || 
        String(item.name).match(/^\d{2}:\d{2}/)
      ))
    );
    
    // Check if data has few categories with values
    const hasFewCategories = data.length <= 5;
    
    // Check if data has multiple series
    const hasMultipleSeries = data[0] && Object.keys(data[0]).length > 2;
    
    if (hasTimePattern && hasMultipleSeries) {
      return 'area';
    } else if (hasTimePattern) {
      return 'line';
    } else if (hasFewCategories) {
      return 'pie';
    } else {
      return 'bar';
    }
  };
  
  // Handle chart type change
  const handleChartTypeChange = (type) => {
    setChartType(type);
  };
  
  // Render appropriate chart based on type
  const renderChart = () => {
    if (!chartData || chartData.length === 0) {
      return (
        <div className="dataviz-empty-content">
          No visualization data available
        </div>
      );
    }
    
    // Get data keys (excluding 'name')
    const dataKeys = Object.keys(chartData[0]).filter(key => key !== 'name');
    
    // Get colors from config or use defaults
    const colors = chartConfig.colors || [
      '#8884d8', '#82ca9d', '#ffc658', '#ff8042', '#0088fe', '#00c49f', '#ffbb28', '#ff8042'
    ];
    
    switch (chartType) {
      case 'line':
        return (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 50 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis 
                dataKey="name" 
                label={{ value: chartConfig.xAxisLabel || '', position: 'bottom', offset: 0 }}
                angle={-45}
                textAnchor="end"
              />
              <YAxis label={{ value: chartConfig.yAxisLabel || '', angle: -90, position: 'insideLeft' }} />
              <Tooltip />
              <Legend />
              {dataKeys.map((key, index) => (
                <Line 
                  key={key}
                  type="monotone" 
                  dataKey={key} 
                  stroke={colors[index % colors.length]} 
                  activeDot={{ r: 8 }}
                  name={chartConfig.labels?.[key] || key}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        );
        
      case 'bar':
        return (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 50 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis 
                dataKey="name" 
                label={{ value: chartConfig.xAxisLabel || '', position: 'bottom', offset: 0 }}
                angle={-45}
                textAnchor="end"
              />
              <YAxis label={{ value: chartConfig.yAxisLabel || '', angle: -90, position: 'insideLeft' }} />
              <Tooltip />
              <Legend />
              {dataKeys.map((key, index) => (
                <Bar 
                  key={key}
                  dataKey={key} 
                  fill={colors[index % colors.length]} 
                  name={chartConfig.labels?.[key] || key}
                />
              ))}
            </BarChart>
          </ResponsiveContainer>
        );
        
      case 'pie':
        return (
          <ResponsiveContainer width="100%" height="100%">
            <PieChart margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
              <Pie
                data={chartData}
                cx="50%"
                cy="50%"
                labelLine={true}
                outerRadius={80}
                fill="#8884d8"
                dataKey={dataKeys[0] || 'value'}
                nameKey="name"
                label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
              >
                {chartData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={colors[index % colors.length]} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        );
        
      case 'area':
        return (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 50 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis 
                dataKey="name" 
                label={{ value: chartConfig.xAxisLabel || '', position: 'bottom', offset: 0 }}
                angle={-45}
                textAnchor="end"
              />
              <YAxis label={{ value: chartConfig.yAxisLabel || '', angle: -90, position: 'insideLeft' }} />
              <Tooltip />
              <Legend />
              {dataKeys.map((key, index) => (
                <Area 
                  key={key}
                  type="monotone" 
                  dataKey={key} 
                  stackId="1"
                  stroke={colors[index % colors.length]} 
                  fill={colors[index % colors.length]} 
                  name={chartConfig.labels?.[key] || key}
                />
              ))}
            </AreaChart>
          </ResponsiveContainer>
        );
        
      default:
        return (
          <div className="dataviz-empty-content">
            Unsupported chart type: {chartType}
          </div>
        );
    }
  };
  
  return (
    <div className="dataviz-output">
      <div className="dataviz-toolbar">
        <div className="dataviz-chart-types">
          <button 
            className={`dataviz-type-button ${chartType === 'line' ? 'active' : ''}`}
            onClick={() => handleChartTypeChange('line')}
            title="Line Chart"
          >
            📈
          </button>
          
          <button 
            className={`dataviz-type-button ${chartType === 'bar' ? 'active' : ''}`}
            onClick={() => handleChartTypeChange('bar')}
            title="Bar Chart"
          >
            📊
          </button>
          
          <button 
            className={`dataviz-type-button ${chartType === 'pie' ? 'active' : ''}`}
            onClick={() => handleChartTypeChange('pie')}
            title="Pie Chart"
          >
            🥧
          </button>
          
          <button 
            className={`dataviz-type-button ${chartType === 'area' ? 'active' : ''}`}
            onClick={() => handleChartTypeChange('area')}
            title="Area Chart"
          >
            🏔️
          </button>
        </div>
        
        <div className="dataviz-info">
          <span className="dataviz-data-count">
            {chartData.length} data points
          </span>
          
          <span className="dataviz-status">
            {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
          </span>
        </div>
      </div>
      
      <div className="dataviz-content">
        <div className="dataviz-chart-container">
          {renderChart()}
        </div>
        
        <div className="dataviz-raw-data">
          <div className="dataviz-raw-header">
            Raw Data
          </div>
          <pre className="dataviz-raw-content">
            {rawData}
          </pre>
        </div>
      </div>
    </div>
  );
};

export default DataVizOutput;
