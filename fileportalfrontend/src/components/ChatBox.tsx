import React, { useState, useRef, useEffect } from 'react';
import { chatService } from '../services/chatService';
import '../styles/ChatBox.css';

interface Message {
  id: string;
  type: 'user' | 'ai';
  content: string;
  timestamp: Date;
}

export const ChatBox: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  const handleSend = async () => {
    if (!input.trim() || isLoading) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      type: 'user',
      content: input.trim(),
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const response = await chatService.query(userMessage.content);

      const aiMessage: Message = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: response.answer,
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, aiMessage]);
    } catch (error) {
      console.error('Failed to get AI response:', error);

      const errorMessage: Message = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: 'Sorry, I encountered an error processing your request. Please try again.',
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleExampleClick = (example: string) => {
    setInput(example);
  };

  const handleClear = () => {
    setMessages([]);
  };

  const formatTime = (date: Date) => {
    return date.toLocaleTimeString('it-IT', {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <>
      {/* Floating Button */}
      <button
        className="chat-toggle-btn"
        onClick={() => setIsOpen(!isOpen)}
        title={isOpen ? 'Close chat' : 'Open AI assistant'}
      >
        {isOpen ? '✕' : '💬'}
      </button>

      {/* Chat Container */}
      {isOpen && (
        <div className="chat-container">
          {/* Header */}
          <div className="chat-header">
            <div className="chat-title">
              <span className="chat-icon">🤖</span>
              <span>AI Assistant</span>
            </div>
            <div className="chat-actions">
              {messages.length > 0 && (
                <button
                  className="clear-btn"
                  onClick={handleClear}
                  title="Clear conversation"
                >
                  🗑️
                </button>
              )}
              <button
                className="close-btn"
                onClick={() => setIsOpen(false)}
                title="Close chat"
              >
                ✕
              </button>
            </div>
          </div>

          {/* Messages */}
          <div className="chat-messages">
            {messages.length === 0 ? (
              <div className="chat-welcome">
                <p>👋 Welcome to the AI Assistant!</p>
                <p>Ask me anything about your files and documents.</p>

                <div className="chat-examples">
                  <p>Try asking:</p>
                  <button
                    className="example-btn"
                    onClick={() => handleExampleClick('Summarize my recent documents')}
                  >
                    📄 Summarize my recent documents
                  </button>
                  <button
                    className="example-btn"
                    onClick={() => handleExampleClick('Find files related to project planning')}
                  >
                    🔍 Find files related to project planning
                  </button>
                </div>
              </div>
            ) : (
              <>
                {messages.map((message) => (
                  <div key={message.id} className="message-group">
                    <div className={`message ${message.type}-message`}>
                      <div className="message-header">
                        <span className="message-icon">
                          {message.type === 'user' ? '👤' : '🤖'}
                        </span>
                        <span className="message-label">
                          {message.type === 'user' ? 'You' : 'AI Assistant'}
                        </span>
                        <span className="message-time">
                          {formatTime(message.timestamp)}
                        </span>
                      </div>
                      <div className="message-content">
                        <div className="answer-text">{message.content}</div>
                      </div>
                    </div>
                  </div>
                ))}
                {isLoading && (
                  <div className="message-group">
                    <div className="message ai-message">
                      <div className="message-header">
                        <span className="message-icon">🤖</span>
                        <span className="message-label">AI Assistant</span>
                      </div>
                      <div className="message-content">
                        <div className="loading-dots">
                          <span></span>
                          <span></span>
                          <span></span>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Input */}
          <div className="chat-input-container">
            <textarea
              ref={inputRef}
              className="chat-input"
              placeholder="Type your message..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyPress}
              disabled={isLoading}
              rows={1}
            />
            <button
              className="send-btn"
              onClick={handleSend}
              disabled={!input.trim() || isLoading}
              title="Send message"
            >
              ➤
            </button>
          </div>
        </div>
      )}
    </>
  );
};

